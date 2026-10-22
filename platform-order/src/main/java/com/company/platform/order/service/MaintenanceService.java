package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizDeviceQueue;
import com.company.platform.order.entity.BizMaintenanceOrder;
import com.company.platform.order.mapper.BizDeviceMapper;
import com.company.platform.order.mapper.BizDeviceQueueMapper;
import com.company.platform.order.mapper.BizMaintenanceOrderMapper;
import com.company.platform.order.web.DeviceStatusCommand;
import com.company.platform.order.web.MaintenanceCompleteCommand;
import com.company.platform.order.web.MaintenanceCreateCommand;
import com.company.platform.order.web.MaintenancePageView;
import com.company.platform.order.web.MaintenanceSummaryView;
import com.company.platform.order.web.MaintenanceView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 设备维护工单服务。
 *
 * <p><strong>与排产的关系：</strong>开始维护时设备转为保养或故障状态，等待
 * 队列保留（不取消、不改顺序），并给受影响工单的业务员发送交期风险消息；
 * 维护完成后设备恢复空闲，队列计划时间按新的空闲时间重算。生产中的设备不能
 * 停机，必须先完成当前工单。</p>
 *
 * <p><strong>成本口径：</strong>维护费用全部由公司承担，只做成本记录，不生成
 * 应收、不向客户收取，也不计算设备使用费；金额以分存储。</p>
 *
 * <p><strong>数据范围：</strong>厂长只能处理本厂设备，管理员可跨厂；销售只有
 * 只读权限，用于了解交期风险。</p>
 */
@Service
public class MaintenanceService {

    /** 维护类型：预防性保养。 */
    public static final String TYPE_PREVENTIVE = "PREVENTIVE";
    /** 维护类型：故障维修。 */
    public static final String TYPE_REPAIR = "REPAIR";
    /** 维护类型：点检异常整改。 */
    public static final String TYPE_INSPECTION = "INSPECTION";

    private static final Set<String> TYPES =
            Set.of(TYPE_PREVENTIVE, TYPE_REPAIR, TYPE_INSPECTION);
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    private final BizMaintenanceOrderMapper maintenanceMapper;
    private final BizDeviceMapper deviceMapper;
    private final BizDeviceQueueMapper queueMapper;
    private final DeviceService deviceService;
    private final WorkOrderService workOrderService;
    private final StationMessageService stationMessageService;
    private final SnowflakeIdGenerator idGenerator;

    public MaintenanceService(BizMaintenanceOrderMapper maintenanceMapper,
                              BizDeviceMapper deviceMapper,
                              BizDeviceQueueMapper queueMapper,
                              DeviceService deviceService,
                              WorkOrderService workOrderService,
                              StationMessageService stationMessageService,
                              @Value("${app.snowflake.worker-id:1}") long workerId,
                              @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.maintenanceMapper = maintenanceMapper;
        this.deviceMapper = deviceMapper;
        this.queueMapper = queueMapper;
        this.deviceService = deviceService;
        this.workOrderService = workOrderService;
        this.stationMessageService = stationMessageService;
        this.idGenerator = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    /**
     * 写入数据范围校验。
     *
     * <p>维护与点检写入都要求能操作该工厂：管理员和机器令牌不受限，厂长只能
     * 操作本厂；其他角色没有维护写入权限。</p>
     */
    public void requireFactoryWriteAccess(Long factoryId) {
        deviceService.requireFactoryAccess(factoryId);
    }

    /**
     * 维护工单分页。
     *
     * @param pageNumber 页码
     * @param pageSize   每页条数
     * @param keyword    维护单号/设备编号/主题关键字
     * @param deviceId   设备过滤
     * @param factoryId  工厂过滤
     * @param status     状态过滤
     * @param type       维护类型过滤
     */
    @Transactional(readOnly = true)
    public MaintenancePageView page(long pageNumber, long pageSize, String keyword, Long deviceId,
                                    Long factoryId, String status, String type) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        appendFilter(conditions, arguments, keyword, deviceId, factoryId, status, type);
        appendFactoryScope(conditions, arguments);

        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false).orderBy("id", false);
        Page<BizMaintenanceOrder> page = maintenanceMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<MaintenanceView> records = buildViews(page.getRecords());

        // 汇总复用相同筛选条件，额外查询全部匹配记录统计状态与成本。
        QueryWrapper summaryQuery = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            summaryQuery.where(String.join(" AND ", conditions), arguments.toArray());
        }
        List<BizMaintenanceOrder> all = maintenanceMapper.selectListByQuery(summaryQuery);
        long pending = 0;
        long inProgress = 0;
        long completed = 0;
        long cancelled = 0;
        long cost = 0L;
        for (BizMaintenanceOrder order : all) {
            switch (order.getStatus() == null ? "" : order.getStatus()) {
                case "PENDING" -> pending++;
                case "IN_PROGRESS" -> inProgress++;
                case "COMPLETED" -> completed++;
                case "CANCELLED" -> cancelled++;
                default -> {
                    // 未知状态不参与统计。
                }
            }
            if (order.getCostAmount() != null) {
                cost += order.getCostAmount();
            }
        }
        MaintenanceSummaryView summary =
                new MaintenanceSummaryView(all.size(), pending, inProgress, completed, cancelled, cost);
        return new MaintenancePageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(), summary);
    }

    /** 维护工单详情。 */
    @Transactional(readOnly = true)
    public MaintenanceView get(long id) {
        BizMaintenanceOrder order = maintenanceMapper.selectOneById(id);
        if (order == null) {
            throw new BizException(404, "维护工单不存在：" + id);
        }
        requireReadAccess(order.getFactoryId());
        return buildView(order);
    }

    /** 某台设备的维护工单，按创建时间倒序。 */
    @Transactional(readOnly = true)
    public List<MaintenanceView> listByDevice(long deviceId) {
        BizDevice device = deviceMapper.selectOneById(deviceId);
        if (device == null) {
            throw new BizException(404, "设备不存在：" + deviceId);
        }
        requireReadAccess(device.getFactoryId());
        List<BizMaintenanceOrder> orders = maintenanceMapper.selectListByQuery(QueryWrapper.create()
                .where("device_id = ?", deviceId)
                .orderBy("created_at", false));
        return buildViews(orders);
    }

    /**
     * 新建维护工单。
     *
     * <p>工厂强制取设备归属工厂；执行人默认为当前用户。工单初始为待处理，
     * 此时设备状态不变，排产不受影响。</p>
     */
    @Transactional
    public MaintenanceView create(MaintenanceCreateCommand command) {
        BizDevice device = deviceMapper.selectOneById(command.deviceId());
        if (device == null) {
            throw new BizException(404, "设备不存在：" + command.deviceId());
        }
        deviceService.requireFactoryAccess(device.getFactoryId());
        String type = command.maintenanceType().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new BizException(400, "维护类型只能是预防性保养、故障维修或点检整改");
        }
        String priority = command.priority().trim().toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(priority)) {
            throw new BizException(400, "优先级只能是低、普通、高或紧急");
        }
        if (command.plannedStart() != null && command.plannedEnd() != null
                && command.plannedEnd().isBefore(command.plannedStart())) {
            throw new BizException(400, "计划结束时间不能早于计划开始时间");
        }

        LocalDateTime now = LocalDateTime.now();
        BizMaintenanceOrder order = new BizMaintenanceOrder();
        order.setMaintenanceNo("MT-" + idGenerator.nextId());
        order.setDeviceId(device.getId());
        order.setDeviceNo(device.getDeviceNo());
        order.setFactoryId(device.getFactoryId());
        order.setMaintenanceType(type);
        order.setTitle(command.title().trim());
        order.setDescription(trimToNull(command.description()));
        order.setPriority(priority);
        order.setStatus("PENDING");
        order.setPlannedStart(command.plannedStart());
        order.setPlannedEnd(command.plannedEnd());
        order.setDowntimeMinutes(0);
        order.setCostAmount(0L);
        order.setCostNote(null);
        order.setHandlerUserId(command.handlerUserId() == null ? SecurityUtils.userId() : command.handlerUserId());
        order.setHandlerName(handlerName(command.handlerUserId()));
        order.setInspectionId(command.inspectionId());
        order.setRemark(trimToNull(command.remark()));
        order.setCreatedBy(SecurityUtils.userId());
        order.setCreatedAt(now);
        order.setUpdatedBy(SecurityUtils.userId());
        order.setUpdatedAt(now);
        order.setVersion(0);
        maintenanceMapper.insert(order);
        return buildView(order);
    }

    /**
     * 开始维护：设备转为保养或故障状态。
     *
     * <p>生产中的设备不允许停机，必须先完成当前工单。等待队列保留原顺序，
     * 相关业务员会收到交期风险消息。</p>
     */
    @Transactional
    public MaintenanceView start(long id) {
        BizMaintenanceOrder order = maintenanceMapper.selectForUpdate(id);
        if (order == null) {
            throw new BizException(404, "维护工单不存在：" + id);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException(409, "只有待处理的维护工单可以开始，当前状态：" + order.getStatus());
        }
        BizDevice device = deviceMapper.selectForUpdate(order.getDeviceId());
        if (device == null) {
            throw new BizException(404, "设备不存在：" + order.getDeviceId());
        }
        deviceService.requireFactoryAccess(device.getFactoryId());
        String target = TYPE_REPAIR.equals(order.getMaintenanceType()) ? "FAULT" : "MAINTENANCE";
        String reason = "维护工单 " + order.getMaintenanceNo() + "：" + order.getTitle();

        // 复用设备状态变更逻辑：写状态日志、检查是否在生产中、通知受影响业务员。
        deviceService.changeStatus(device.getId(), new DeviceStatusCommand(target, reason));

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("IN_PROGRESS");
        order.setActualStart(now);
        order.setHandlerUserId(SecurityUtils.userId());
        order.setHandlerName(handlerName(null));
        order.setUpdatedBy(SecurityUtils.userId());
        order.setUpdatedAt(now);
        maintenanceMapper.update(order);
        return buildView(order);
    }

    /**
     * 完成维护：记录成本与停机时长，设备恢复空闲并重算队列计划时间。
     *
     * <p>成本由公司承担，只做记录；停机时长未填写时按实际开始到结束时间
     * 自动计算。设备恢复后给等待队列上的业务员发送恢复通知。</p>
     */
    @Transactional
    public MaintenanceView complete(long id, MaintenanceCompleteCommand command) {
        BizMaintenanceOrder order = maintenanceMapper.selectForUpdate(id);
        if (order == null) {
            throw new BizException(404, "维护工单不存在：" + id);
        }
        if (!"IN_PROGRESS".equals(order.getStatus())) {
            throw new BizException(409, "只有执行中的维护工单可以完成，当前状态：" + order.getStatus());
        }
        BizDevice device = deviceMapper.selectForUpdate(order.getDeviceId());
        if (device == null) {
            throw new BizException(404, "设备不存在：" + order.getDeviceId());
        }
        deviceService.requireFactoryAccess(device.getFactoryId());

        LocalDateTime now = LocalDateTime.now();
        long downtime = command.downtimeMinutes() == null
                ? computeDowntime(order.getActualStart(), now)
                : command.downtimeMinutes();

        order.setStatus("COMPLETED");
        order.setActualEnd(now);
        order.setDowntimeMinutes((int) downtime);
        order.setCostAmount(command.costAmount());
        order.setCostNote(trimToNull(command.costNote()));
        order.setUpdatedBy(SecurityUtils.userId());
        order.setUpdatedAt(now);
        maintenanceMapper.update(order);

        // 设备恢复空闲：复用状态变更逻辑写日志；此时不可能有运行中的工单。
        deviceService.changeStatus(device.getId(),
                new DeviceStatusCommand("IDLE", "维护工单 " + order.getMaintenanceNo() + " 已完成"));

        // 队列计划时间按恢复后的空闲时间重算，等待顺序保持不变。
        BizDevice refreshed = deviceMapper.selectOneById(device.getId());
        workOrderService.replanDeviceQueue(refreshed);

        List<BizDeviceQueue> queues = queueMapper.selectActiveByDevice(device.getId());
        if (!queues.isEmpty()) {
            stationMessageService.notifyDeviceRestored(refreshed, queues, order.getMaintenanceNo());
        }
        return buildView(order);
    }

    /** 取消待处理的维护工单；执行中的工单必须先完成。 */
    @Transactional
    public MaintenanceView cancel(long id) {
        BizMaintenanceOrder order = maintenanceMapper.selectForUpdate(id);
        if (order == null) {
            throw new BizException(404, "维护工单不存在：" + id);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException(409, "只有待处理的维护工单可以取消，当前状态：" + order.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus("CANCELLED");
        order.setUpdatedBy(SecurityUtils.userId());
        order.setUpdatedAt(now);
        maintenanceMapper.update(order);
        return buildView(order);
    }

    /** 追加筛选条件；参数全部使用占位符。 */
    private void appendFilter(List<String> conditions, List<Object> arguments, String keyword,
                              Long deviceId, Long factoryId, String status, String type) {
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(maintenance_no LIKE ? OR device_no LIKE ? OR title LIKE ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (deviceId != null) {
            conditions.add("device_id = ?");
            arguments.add(deviceId);
        }
        if (factoryId != null) {
            conditions.add("factory_id = ?");
            arguments.add(factoryId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (type != null && !type.isBlank()) {
            conditions.add("maintenance_type = ?");
            arguments.add(type.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** 追加工厂数据范围：仅厂长受限。 */
    private void appendFactoryScope(List<String> conditions, List<Object> arguments) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            Long factoryId = SecurityUtils.factoryId();
            conditions.add("factory_id = ?");
            arguments.add(factoryId == null ? -1L : factoryId);
        }
    }

    /**
     * 只读数据范围校验。
     *
     * <p>维护工单是设备维度的信息，不含客户或订单信息，因此销售可以查看全部
     * 工厂的维护情况，用于判断交期风险；厂长只看本厂。</p>
     */
    private void requireReadAccess(Long factoryId) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE", "SALES")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            Long ownFactoryId = SecurityUtils.factoryId();
            if (ownFactoryId == null || !ownFactoryId.equals(factoryId)) {
                throw new BizException(403, "无权查看其他工厂的维护工单");
            }
            return;
        }
        throw new BizException(403, "无权查看维护工单");
    }

    /** 批量构建视图，顺带补齐设备名称。 */
    private List<MaintenanceView> buildViews(List<BizMaintenanceOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> deviceIds = new LinkedHashSet<>();
        for (BizMaintenanceOrder order : orders) {
            deviceIds.add(order.getDeviceId());
        }
        Map<Long, BizDevice> devices = new java.util.HashMap<>();
        if (!deviceIds.isEmpty()) {
            List<BizDevice> list = deviceMapper.selectListByQuery(QueryWrapper.create()
                    .where("id IN (" + placeholders(deviceIds.size()) + ")", deviceIds.toArray()));
            for (BizDevice device : list) {
                devices.put(device.getId(), device);
            }
        }
        List<MaintenanceView> views = new ArrayList<>(orders.size());
        for (BizMaintenanceOrder order : orders) {
            BizDevice device = devices.get(order.getDeviceId());
            views.add(MaintenanceView.from(order, device == null ? null : device.getDeviceName()));
        }
        return views;
    }

    /** 构建单个视图。 */
    private MaintenanceView buildView(BizMaintenanceOrder order) {
        BizDevice device = deviceMapper.selectOneById(order.getDeviceId());
        return MaintenanceView.from(order, device == null ? null : device.getDeviceName());
    }

    /** 停机时长：按实际开始到结束时间计算，最少 0 分钟。 */
    private static long computeDowntime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0L;
        }
        return Duration.between(start, end).toMinutes();
    }

    /** 执行人姓名：指定执行人时使用当前用户昵称兜底。 */
    private static String handlerName(Long handlerUserId) {
        if (handlerUserId == null || handlerUserId.equals(SecurityUtils.userId())) {
            return SecurityUtils.nickname();
        }
        return "用户#" + handlerUserId;
    }

    /** 生成 IN 子句占位符。 */
    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    /** 去首尾空格，空字符串转 null。 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
