package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizDeviceQueue;
import com.company.platform.order.entity.BizDeviceStatusLog;
import com.company.platform.order.entity.BizWorkOrder;
import com.company.platform.order.mapper.BizDeviceMapper;
import com.company.platform.order.mapper.BizDeviceQueueMapper;
import com.company.platform.order.mapper.BizDeviceStatusLogMapper;
import com.company.platform.order.mapper.BizWorkOrderMapper;
import com.company.platform.order.web.DeviceCreateCommand;
import com.company.platform.order.web.DevicePageView;
import com.company.platform.order.web.DeviceStatusCommand;
import com.company.platform.order.web.DeviceSummaryView;
import com.company.platform.order.web.DeviceUpdateCommand;
import com.company.platform.order.web.DeviceView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 设备台账服务。
 *
 * <p>负责设备档案的创建、修改、状态变更和查询。设备只归属一个工厂，编号
 * 全公司唯一；设备能力字段是排产匹配的依据，不绑定客户。</p>
 *
 * <p><strong>状态规则：</strong></p>
 * <ul>
 *   <li>新建设备默认空闲 {@code IDLE}。</li>
 *   <li>{@code RUNNING} 只能由工单开始动作进入，人工状态接口不能直接设置。</li>
 *   <li>设备正在生产时禁止改为保养/故障/停用，必须先完成工单；只有等待队列
 *       时允许停机，队列保留，恢复空闲后才能继续开始工单。</li>
 *   <li>每次状态变化都追加 {@code t_device_status_log}，不修改历史。</li>
 * </ul>
 */
@Service
public class DeviceService {

    /** 人工可设置的状态；RUNNING 由工单动作维护。 */
    private static final Set<String> MANUAL_STATUSES = Set.of("IDLE", "MAINTENANCE", "FAULT", "DISABLED");
    /** 所有合法状态。 */
    private static final Set<String> ALL_STATUSES = Set.of("IDLE", "RUNNING", "MAINTENANCE", "FAULT", "DISABLED");

    private final BizDeviceMapper deviceMapper;
    private final BizDeviceStatusLogMapper statusLogMapper;
    private final BizDeviceQueueMapper queueMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final StationMessageService stationMessageService;

    public DeviceService(BizDeviceMapper deviceMapper,
                         BizDeviceStatusLogMapper statusLogMapper,
                         BizDeviceQueueMapper queueMapper,
                         BizWorkOrderMapper workOrderMapper,
                         StationMessageService stationMessageService) {
        this.deviceMapper = deviceMapper;
        this.statusLogMapper = statusLogMapper;
        this.queueMapper = queueMapper;
        this.workOrderMapper = workOrderMapper;
        this.stationMessageService = stationMessageService;
    }

    /**
     * 新建设备。
     *
     * <p>编号重复直接拒绝；门幅区间和良率必须在合法范围。设备创建后立即写
     * 一条状态日志，便于台账追溯初始状态。</p>
     */
    @Transactional
    public DeviceView create(DeviceCreateCommand command) {
        requireFactoryAccess(command.factoryId());
        String deviceNo = command.deviceNo().trim().toUpperCase(Locale.ROOT);
        long duplicated = deviceMapper.selectCountByQuery(QueryWrapper.create()
                .where("device_no = ?", deviceNo));
        if (duplicated > 0) {
            throw new BizException(409, "设备编号已存在：" + deviceNo);
        }
        validateCapability(command.minWidthMm(), command.maxWidthMm(), command.yieldRate());
        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();

        BizDevice device = new BizDevice();
        device.setDeviceNo(deviceNo);
        device.setDeviceName(command.deviceName().trim());
        device.setDeviceType(command.deviceType().trim());
        device.setModel(trimToNull(command.model()));
        device.setFactoryId(command.factoryId());
        device.setWorkshop(trimToNull(command.workshop()));
        device.setLocation(trimToNull(command.location()));
        device.setStatus("IDLE");
        device.setMaterialTypes(normalizeTokens(command.materialTypes()));
        device.setMinWidthMm(command.minWidthMm());
        device.setMaxWidthMm(command.maxWidthMm());
        device.setProcessTypes(normalizeTokens(command.processTypes()));
        device.setCapacityMetersPerHour(command.capacityMetersPerHour());
        device.setChangeoverMinutes(command.changeoverMinutes() == null ? 0 : command.changeoverMinutes());
        device.setYieldRate(command.yieldRate());
        device.setOperatorRequirement(trimToNull(command.operatorRequirement()));
        device.setRemark(trimToNull(command.remark()));
        device.setCreatedBy(userId);
        device.setCreatedAt(now);
        device.setUpdatedBy(userId);
        device.setUpdatedAt(now);
        device.setVersion(0);
        deviceMapper.insert(device);
        writeStatusLog(device.getId(), null, "IDLE", "新建设备", null, userId, now);
        return buildView(device);
    }

    /**
     * 修改设备能力与位置。设备编号、所属工厂不允许通过本接口修改；跨厂移机
     * 需要审批并同步重排队列，留待移机流程实现。
     */
    @Transactional
    public DeviceView update(long id, DeviceUpdateCommand command) {
        BizDevice device = requireDevice(id);
        requireFactoryAccess(device.getFactoryId());
        validateCapability(command.minWidthMm(), command.maxWidthMm(), command.yieldRate());
        device.setDeviceName(command.deviceName().trim());
        device.setDeviceType(command.deviceType().trim());
        device.setModel(trimToNull(command.model()));
        device.setWorkshop(trimToNull(command.workshop()));
        device.setLocation(trimToNull(command.location()));
        device.setMaterialTypes(normalizeTokens(command.materialTypes()));
        device.setMinWidthMm(command.minWidthMm());
        device.setMaxWidthMm(command.maxWidthMm());
        device.setProcessTypes(normalizeTokens(command.processTypes()));
        device.setCapacityMetersPerHour(command.capacityMetersPerHour());
        device.setChangeoverMinutes(command.changeoverMinutes() == null ? 0 : command.changeoverMinutes());
        device.setYieldRate(command.yieldRate());
        device.setOperatorRequirement(trimToNull(command.operatorRequirement()));
        device.setRemark(trimToNull(command.remark()));
        device.setUpdatedBy(SecurityUtils.userId());
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.update(device);
        return buildView(device);
    }

    /**
     * 人工变更设备状态。
     *
     * <p>正在生产的设备不允许停机，避免队列出现“生产中但设备不可用”的矛盾
     * 状态。设备仍有等待队列时允许停机，队列保留；恢复空闲后由排产人员继续
     * 开始队首工单。停机时给受影响的业务员发送站内消息提示交期风险。</p>
     */
    @Transactional
    public DeviceView changeStatus(long id, DeviceStatusCommand command) {
        BizDevice device = deviceMapper.selectForUpdate(id);
        if (device == null) {
            throw new BizException(404, "设备不存在：" + id);
        }
        requireFactoryAccess(device.getFactoryId());
        String target = command.status().trim().toUpperCase(Locale.ROOT);
        if (!MANUAL_STATUSES.contains(target)) {
            throw new BizException(400, "只能设置空闲、保养、故障或停用状态");
        }
        if (!ALL_STATUSES.contains(device.getStatus())) {
            throw new BizException(409, "设备当前状态异常：" + device.getStatus());
        }
        if (target.equals(device.getStatus())) {
            return buildView(device);
        }
        List<BizDeviceQueue> activeQueues = queueMapper.selectActiveForUpdate(device.getId());
        boolean running = activeQueues.stream().anyMatch(item -> "RUNNING".equals(item.getStatus()));
        if (running) {
            throw new BizException(409, "设备正在生产，请先完成当前工单后再变更状态");
        }
        String fromStatus = device.getStatus();
        device.setStatus(target);
        device.setUpdatedBy(SecurityUtils.userId());
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.update(device);
        writeStatusLog(device.getId(), fromStatus, target, trimToNull(command.reason()),
                device.getCurrentWorkOrderId(), SecurityUtils.userId(), device.getUpdatedAt());
        if (!"IDLE".equals(target) && !activeQueues.isEmpty()) {
            stationMessageService.notifyDeviceUnavailable(device, target, activeQueues, command.reason());
        }
        return buildView(device);
    }

    /**
     * 设备台账分页。
     *
     * <p>关键字匹配编号、名称、类型和型号；厂长只看本厂，管理员/财务/机器令牌
     * 看全部。汇总指标按筛选条件统计，不受分页影响。</p>
     */
    @Transactional(readOnly = true)
    public DevicePageView page(long pageNumber, long pageSize, String keyword, Long factoryId, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        appendFilter(conditions, arguments, keyword, factoryId, status);
        appendFactoryScope(conditions, arguments);

        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("factory_id", true).orderBy("device_no", true);
        Page<BizDevice> page = deviceMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<DeviceView> records = buildViews(page.getRecords());

        // 汇总复用相同筛选条件，额外查询全部匹配设备以统计状态。
        QueryWrapper summaryQuery = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            summaryQuery.where(String.join(" AND ", conditions), arguments.toArray());
        }
        List<BizDevice> all = deviceMapper.selectListByQuery(summaryQuery);
        long idle = 0;
        long running = 0;
        long maintenance = 0;
        long fault = 0;
        long disabled = 0;
        for (BizDevice device : all) {
            switch (device.getStatus()) {
                case "IDLE" -> idle++;
                case "RUNNING" -> running++;
                case "MAINTENANCE" -> maintenance++;
                case "FAULT" -> fault++;
                case "DISABLED" -> disabled++;
                default -> {
                    // 未知状态不参与汇总，避免脏数据导致统计异常。
                }
            }
        }
        long queued = countWaitingQueues(all);
        DeviceSummaryView summary = new DeviceSummaryView(all.size(), idle, running, maintenance, fault,
                disabled, queued);
        return new DevicePageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(), summary);
    }

    /**
     * 设备下拉选项，供排产和插队页面使用。
     *
     * <p>返回全部未停用设备；厂长仍按本厂过滤，销售和其他只读角色可以看到
     * 全部工厂的设备队列，便于跟踪自己订单的排产情况。</p>
     */
    @Transactional(readOnly = true)
    public List<DeviceView> options(Long factoryId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        conditions.add("status <> 'DISABLED'");
        if (factoryId != null) {
            conditions.add("factory_id = ?");
            arguments.add(factoryId);
        }
        appendFactoryScope(conditions, arguments);
        QueryWrapper query = QueryWrapper.create()
                .where(String.join(" AND ", conditions), arguments.toArray())
                .orderBy("factory_id", true)
                .orderBy("device_no", true);
        return buildViews(deviceMapper.selectListByQuery(query));
    }

    /** 设备详情；不存在或超出数据范围时抛异常。 */
    @Transactional(readOnly = true)
    public DeviceView get(long id) {
        return buildView(requireAccessible(id));
    }

    /** 读取设备并校验当前用户是否有权访问其工厂。 */
    @Transactional(readOnly = true)
    public BizDevice requireAccessible(long id) {
        BizDevice device = requireDevice(id);
        requireFactoryAccess(device.getFactoryId());
        return device;
    }

    /** 读取设备，不存在时抛 404。 */
    @Transactional(readOnly = true)
    public BizDevice requireDevice(long id) {
        BizDevice device = deviceMapper.selectOneById(id);
        if (device == null) {
            throw new BizException(404, "设备不存在：" + id);
        }
        return device;
    }

    /** 校验当前用户能否操作指定工厂；厂长只能操作本厂。 */
    public void requireFactoryAccess(Long factoryId) {
        if (factoryId == null) {
            throw new BizException(400, "设备必须归属工厂");
        }
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            Long ownFactoryId = SecurityUtils.factoryId();
            if (ownFactoryId == null || !ownFactoryId.equals(factoryId)) {
                throw new BizException(403, "只能操作本厂设备");
            }
            return;
        }
        // 其他角色即使拥有只读权限，也不能通过设备接口写入；写接口已由权限码控制。
        throw new BizException(403, "无权操作设备台账");
    }

    /** 构建单个设备视图，补齐当前工单和队列统计。 */
    private DeviceView buildView(BizDevice device) {
        List<BizDeviceQueue> queues = queueMapper.selectActiveByDevice(device.getId());
        BizWorkOrder current = null;
        if (device.getCurrentWorkOrderId() != null) {
            current = workOrderMapper.selectOneById(device.getCurrentWorkOrderId());
            // 兼容历史脏数据：只有生产中工单才显示为当前工单。
            if (current != null && !"RUNNING".equals(current.getStatus())) {
                current = null;
            }
        }
        LocalDateTime nextFreeAt = queues.isEmpty() ? null : queues.get(queues.size() - 1).getPlannedEnd();
        return DeviceView.from(device, current == null ? null : current.getWorkOrderNo(),
                current == null ? null : current.getOrderNo(), queues.size(), nextFreeAt);
    }

    /**
     * 批量构建设备视图。
     *
     * <p>先一次性查询当前工单，再一次性查询所有活跃队列，避免设备列表逐行
     * 查询造成 N+1。数据量较大时应改为分页专用 SQL 聚合。</p>
     */
    private List<DeviceView> buildViews(List<BizDevice> devices) {
        if (devices.isEmpty()) {
            return List.of();
        }
        Set<Long> workOrderIds = new HashSet<>();
        List<Long> deviceIds = new ArrayList<>();
        for (BizDevice device : devices) {
            deviceIds.add(device.getId());
            if (device.getCurrentWorkOrderId() != null) {
                workOrderIds.add(device.getCurrentWorkOrderId());
            }
        }
        Map<Long, BizWorkOrder> workOrders = loadWorkOrders(workOrderIds);
        List<BizDeviceQueue> queues = queueMapper.selectActiveByDeviceIds(deviceIds);
        Map<Long, List<BizDeviceQueue>> queueMap = new HashMap<>();
        for (BizDeviceQueue queue : queues) {
            queueMap.computeIfAbsent(queue.getDeviceId(), key -> new ArrayList<>()).add(queue);
        }
        List<DeviceView> views = new ArrayList<>(devices.size());
        for (BizDevice device : devices) {
            List<BizDeviceQueue> deviceQueues = queueMap.getOrDefault(device.getId(), List.of());
            BizWorkOrder current = device.getCurrentWorkOrderId() == null
                    ? null : workOrders.get(device.getCurrentWorkOrderId());
            if (current != null && !"RUNNING".equals(current.getStatus())) {
                current = null;
            }
            LocalDateTime nextFreeAt = deviceQueues.isEmpty()
                    ? null : deviceQueues.get(deviceQueues.size() - 1).getPlannedEnd();
            views.add(DeviceView.from(device, current == null ? null : current.getWorkOrderNo(),
                    current == null ? null : current.getOrderNo(), deviceQueues.size(), nextFreeAt));
        }
        return views;
    }

    /** 批量加载工单，空集合直接返回空 Map。 */
    private Map<Long, BizWorkOrder> loadWorkOrders(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<BizWorkOrder> list = workOrderMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
        Map<Long, BizWorkOrder> result = new HashMap<>();
        for (BizWorkOrder workOrder : list) {
            result.put(workOrder.getId(), workOrder);
        }
        return result;
    }

    /** 统计给定设备的等待中工单数量。 */
    private long countWaitingQueues(List<BizDevice> devices) {
        if (devices.isEmpty()) {
            return 0;
        }
        List<Long> ids = devices.stream().map(BizDevice::getId).toList();
        return queueMapper.selectCountByQuery(QueryWrapper.create()
                .where("status = 'WAITING' AND device_id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
    }

    /** 追加台账筛选条件；参数全部使用占位符，避免 SQL 注入。 */
    private void appendFilter(List<String> conditions, List<Object> arguments, String keyword,
                              Long factoryId, String status) {
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(device_no LIKE ? OR device_name LIKE ? OR device_type LIKE ? OR model LIKE ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (factoryId != null) {
            conditions.add("factory_id = ?");
            arguments.add(factoryId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** 追加工厂数据范围：仅厂长受限，管理员/财务/机器令牌看全部。 */
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

    /** 校验门幅区间和良率；空值允许。 */
    private void validateCapability(Integer minWidthMm, Integer maxWidthMm, BigDecimal yieldRate) {
        if (minWidthMm != null && maxWidthMm != null && minWidthMm > maxWidthMm) {
            throw new BizException(400, "最小门幅不能大于最大门幅");
        }
        if (yieldRate != null && (yieldRate.compareTo(BigDecimal.ZERO) < 0
                || yieldRate.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BizException(400, "良率必须在 0–100 之间");
        }
    }

    /** 写入状态日志。 */
    private void writeStatusLog(Long deviceId, String fromStatus, String toStatus, String reason,
                                Long workOrderId, Long userId, LocalDateTime now) {
        BizDeviceStatusLog log = new BizDeviceStatusLog();
        log.setDeviceId(deviceId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setReason(reason);
        log.setWorkOrderId(workOrderId);
        log.setChangedBy(userId);
        log.setChangedAt(now);
        statusLogMapper.insert(log);
    }

    /** 规范化逗号分隔的能力标签：去空格、去重、保留输入顺序，空值返回 null。 */
    private static String normalizeTokens(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        Set<String> tokens = new java.util.LinkedHashSet<>();
        for (String item : text.split("[,，;；]")) {
            String token = item.trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens.isEmpty() ? null : String.join(",", tokens);
    }

    /** 生成 IN 子句占位符，数量来自集合大小而不是用户输入。 */
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
