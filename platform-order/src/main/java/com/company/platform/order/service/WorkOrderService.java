package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizDeviceQueue;
import com.company.platform.order.entity.BizOrder;
import com.company.platform.order.entity.BizWorkOrder;
import com.company.platform.order.mapper.BizDeviceMapper;
import com.company.platform.order.mapper.BizDeviceQueueMapper;
import com.company.platform.order.mapper.BizOrderMapper;
import com.company.platform.order.mapper.BizWorkOrderMapper;
import com.company.platform.order.web.QueueEntryView;
import com.company.platform.order.web.WorkOrderAssignCommand;
import com.company.platform.order.web.WorkOrderCreateCommand;
import com.company.platform.order.web.WorkOrderView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 生产工单与设备队列服务。
 *
 * <p>V1.4 排产规则：订单只能选择一个生产工厂；订单可按材料/工艺/长度拆成
 * 多个工单；每个工单独立选择同工厂设备、独立排队；一台设备同一时间只允许
 * 一个工单处于生产状态；超出产能按进入队列时间 FIFO 等待。</p>
 *
 * <p><strong>时间计算：</strong>预计生产分钟数 = 计划米数 ÷ 设备产能（米/小时）
 * × 60，向上取整到分钟，再加换型时间。金额规则不适用于长度，计划长度保留
 * 2 位小数，由数据库 DECIMAL(12,2) 约束。</p>
 *
 * <p><strong>订单状态：</strong>工单入队后订单进入 QUEUED，任一工单开始后进入
 * IN_PRODUCTION，全部工单完成后进入 COMPLETED。订单表是分片表，可能落在
 * ds_1，而工单表固定在 ds_0；当前使用 ShardingSphere 本地事务尽力保证一致，
 * 生产环境应改为可靠事件/最终一致，避免跨库提交失败造成状态偏差。</p>
 */
@Service
public class WorkOrderService {

    /** 可创建/分配工单的订单状态。 */
    private static final Set<String> SCHEDULABLE_ORDER_STATUSES =
            Set.of("CREATED", "QUEUED", "IN_PRODUCTION");
    /** 可接受新排队的设备状态；生产中的设备也可以继续排队。 */
    private static final Set<String> SCHEDULABLE_DEVICE_STATUSES = Set.of("IDLE", "RUNNING");
    /** 查看工单材料成本所需的权限码。 */
    private static final String COST_READ_PERMISSION = "production:cost:read";

    private final BizWorkOrderMapper workOrderMapper;
    private final BizDeviceMapper deviceMapper;
    private final BizDeviceQueueMapper queueMapper;
    private final BizOrderMapper orderMapper;
    private final DeviceService deviceService;
    private final MaterialIssueService materialIssueService;
    private final SnowflakeIdGenerator idGenerator;

    public WorkOrderService(BizWorkOrderMapper workOrderMapper,
                            BizDeviceMapper deviceMapper,
                            BizDeviceQueueMapper queueMapper,
                            BizOrderMapper orderMapper,
                            DeviceService deviceService,
                            MaterialIssueService materialIssueService,
                            @Value("${app.snowflake.worker-id:1}") long workerId,
                            @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.workOrderMapper = workOrderMapper;
        this.deviceMapper = deviceMapper;
        this.queueMapper = queueMapper;
        this.orderMapper = orderMapper;
        this.deviceService = deviceService;
        this.materialIssueService = materialIssueService;
        this.idGenerator = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    /**
     * 创建生产工单。
     *
     * <p>工厂强制取订单的工厂，请求中不接收工厂字段；如果带设备，则立即
     * 校验能力并进入该设备队列；未带设备时进入待排产，稍后再分配。</p>
     */
    @Transactional
    public WorkOrderView create(WorkOrderCreateCommand command, Long userId) {
        BizOrder order = orderMapper.selectOneById(command.orderNo());
        if (order == null) {
            throw new BizException(404, "订单不存在：" + command.orderNo());
        }
        requireOrderAccess(order);
        if (!SCHEDULABLE_ORDER_STATUSES.contains(order.getOrderStatus())) {
            throw new BizException(409, "订单当前状态不可排产：" + order.getOrderStatus());
        }
        BigDecimal plannedMeters = command.plannedMeters().setScale(2, RoundingMode.DOWN);
        if (plannedMeters.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "计划生产长度必须大于 0");
        }
        LocalDateTime now = LocalDateTime.now();
        BizWorkOrder workOrder = new BizWorkOrder();
        workOrder.setWorkOrderNo("WO-" + idGenerator.nextId());
        workOrder.setOrderNo(order.getOrderNo());
        workOrder.setCustomerId(order.getCustomerId());
        // 责任业务员取订单创建人：销售只能访问自己创建的订单，工单可见范围与
        // 订单数据范围保持一致，插队申请和顺延通知也发给同一人。
        workOrder.setSalesUserId(order.getCreatedBy());
        workOrder.setFactoryId(order.getFactoryId());
        workOrder.setMaterialType(command.materialType().trim());
        workOrder.setProcessType(command.processType().trim());
        workOrder.setFabricWidthMm(command.fabricWidthMm());
        workOrder.setPlannedMeters(plannedMeters);
        workOrder.setStatus("PENDING");
        workOrder.setRemark(trimToNull(command.remark()));
        workOrder.setCreatedBy(userId);
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedBy(userId);
        workOrder.setUpdatedAt(now);
        workOrder.setVersion(0);
        workOrderMapper.insert(workOrder);

        if (command.deviceId() != null) {
            BizDevice device = deviceMapper.selectForUpdate(command.deviceId());
            if (device == null) {
                throw new BizException(404, "设备不存在：" + command.deviceId());
            }
            validateDeviceForWorkOrder(workOrder, device);
            workOrder = enqueue(workOrder, device, now);
        }
        syncOrderStatus(order.getOrderNo());
        return buildView(workOrder);
    }

    /**
     * 给待排产工单分配设备。
     *
     * <p>只允许 PENDING 工单；分配后立即进入设备队尾，按 FIFO 计算预计时间。
     * 设备必须与工单同工厂，能力匹配且状态可用。</p>
     */
    @Transactional
    public WorkOrderView assign(long id, WorkOrderAssignCommand command) {
        BizWorkOrder workOrder = requireForUpdate(id);
        requireWorkOrderAccess(workOrder);
        if (!"PENDING".equals(workOrder.getStatus())) {
            throw new BizException(409, "只有待排产工单可以分配设备");
        }
        BizDevice device = deviceMapper.selectForUpdate(command.deviceId());
        if (device == null) {
            throw new BizException(404, "设备不存在：" + command.deviceId());
        }
        validateDeviceForWorkOrder(workOrder, device);
        LocalDateTime now = LocalDateTime.now();
        workOrder.setUpdatedBy(SecurityUtils.userId());
        workOrder.setUpdatedAt(now);
        workOrder = enqueue(workOrder, device, now);
        syncOrderStatus(workOrder.getOrderNo());
        return buildView(workOrder);
    }

    /**
     * 开始队首工单。
     *
     * <p>设备先加行锁，再锁定活跃队列；只有队列第一条、设备当前没有生产中
     * 工单时才允许开始。开始后设备状态变为 RUNNING，订单进入生产中。</p>
     */
    @Transactional
    public WorkOrderView start(long id) {
        BizWorkOrder workOrder = requireForUpdate(id);
        requireWorkOrderAccess(workOrder);
        if (!"QUEUED".equals(workOrder.getStatus())) {
            throw new BizException(409, "只有排队中的工单可以开始生产");
        }
        BizDevice device = deviceMapper.selectForUpdate(workOrder.getDeviceId());
        if (device == null) {
            throw new BizException(404, "工单尚未分配设备");
        }
        if (!"IDLE".equals(device.getStatus()) && !"RUNNING".equals(device.getStatus())) {
            throw new BizException(409, "设备当前状态不可生产：" + device.getStatus());
        }
        List<BizDeviceQueue> active = queueMapper.selectActiveForUpdate(device.getId());
        BizDeviceQueue ownQueue = findQueue(active, workOrder.getId());
        if (ownQueue == null || !"WAITING".equals(ownQueue.getStatus())) {
            throw new BizException(409, "工单队列状态异常，请刷新后重试");
        }
        BizDeviceQueue firstWaiting = active.stream()
                .filter(item -> "WAITING".equals(item.getStatus()))
                .findFirst()
                .orElse(null);
        if (firstWaiting == null || !firstWaiting.getId().equals(ownQueue.getId())) {
            throw new BizException(409, "队列前面还有工单，不能提前开始");
        }
        boolean otherRunning = active.stream()
                .anyMatch(item -> "RUNNING".equals(item.getStatus()) && !item.getId().equals(ownQueue.getId()));
        if (otherRunning) {
            throw new BizException(409, "设备已有生产中工单，不能并行开始");
        }

        LocalDateTime now = LocalDateTime.now();
        queueMapper.updateStatus(ownQueue.getId(), "RUNNING", now);
        workOrder.setStatus("RUNNING");
        workOrder.setActualStart(now);
        workOrder.setUpdatedBy(SecurityUtils.userId());
        workOrder.setUpdatedAt(now);
        workOrderMapper.update(workOrder);

        device.setStatus("RUNNING");
        device.setCurrentWorkOrderId(workOrder.getId());
        device.setUpdatedBy(SecurityUtils.userId());
        device.setUpdatedAt(now);
        deviceMapper.update(device);
        syncOrderStatus(workOrder.getOrderNo());
        return buildView(workOrder);
    }

    /**
     * 完成生产中工单。
     *
     * <p>工单完成后释放设备，重新计算同一设备剩余等待队列的预计时间；订单
     * 全部工单完成后自动变为已完成。</p>
     */
    @Transactional
    public WorkOrderView complete(long id) {
        BizWorkOrder workOrder = requireForUpdate(id);
        requireWorkOrderAccess(workOrder);
        if (!"RUNNING".equals(workOrder.getStatus())) {
            throw new BizException(409, "只有生产中的工单可以完成");
        }
        BizDevice device = deviceMapper.selectForUpdate(workOrder.getDeviceId());
        if (device == null) {
            throw new BizException(404, "工单设备不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        BizDeviceQueue queue = findQueue(queueMapper.selectActiveForUpdate(device.getId()), workOrder.getId());
        if (queue != null) {
            queueMapper.updateStatus(queue.getId(), "DONE", now);
        }
        workOrder.setStatus("COMPLETED");
        workOrder.setActualEnd(now);
        workOrder.setUpdatedBy(SecurityUtils.userId());
        workOrder.setUpdatedAt(now);
        workOrderMapper.update(workOrder);

        // 用显式 SQL 清空 current_work_order_id；默认 update 会跳过 null 字段。
        deviceMapper.releaseAfterWorkOrder(device.getId(), SecurityUtils.userId(), now);
        device.setStatus("IDLE");
        device.setCurrentWorkOrderId(null);
        replanDeviceQueue(device);
        syncOrderStatus(workOrder.getOrderNo());
        return buildView(workOrder);
    }

    /**
     * 取消待排产或排队中工单。
     *
     * <p>生产中工单不允许直接取消，避免设备占用和产出记录出现空洞；应先完成
     * 或在后续异常处理流程中走专门审批。取消排队工单后重算设备队列。</p>
     */
    @Transactional
    public WorkOrderView cancel(long id) {
        BizWorkOrder workOrder = requireForUpdate(id);
        requireWorkOrderAccess(workOrder);
        if (!"PENDING".equals(workOrder.getStatus()) && !"QUEUED".equals(workOrder.getStatus())) {
            throw new BizException(409, "只有待排产或排队中的工单可以取消");
        }
        LocalDateTime now = LocalDateTime.now();
        BizDevice device = null;
        if ("QUEUED".equals(workOrder.getStatus()) && workOrder.getDeviceId() != null) {
            device = deviceMapper.selectForUpdate(workOrder.getDeviceId());
            BizDeviceQueue queue = findQueue(queueMapper.selectActiveForUpdate(device.getId()), workOrder.getId());
            if (queue != null) {
                if ("RUNNING".equals(queue.getStatus())) {
                    throw new BizException(409, "生产中工单不能直接取消");
                }
                queueMapper.updateStatus(queue.getId(), "CANCELLED", now);
            }
        }
        workOrder.setStatus("CANCELLED");
        workOrder.setUpdatedBy(SecurityUtils.userId());
        workOrder.setUpdatedAt(now);
        workOrderMapper.update(workOrder);
        if (device != null) {
            replanDeviceQueue(device);
        }
        syncOrderStatus(workOrder.getOrderNo());
        return buildView(workOrder);
    }

    /**
     * 工单分页。
     *
     * <p>销售只看自己订单产生的工单；厂长看本厂；管理员/财务/机器令牌看全部。
     * 队列位置实时计算，不依赖数据库中的冗余位置字段。</p>
     */
    @Transactional(readOnly = true)
    public PageResult<WorkOrderView> page(long pageNumber, long pageSize, Long orderNo, Long factoryId,
                                          Long deviceId, String status, String keyword) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (orderNo != null) {
            conditions.add("order_no = ?");
            arguments.add(orderNo);
        }
        if (factoryId != null) {
            conditions.add("factory_id = ?");
            arguments.add(factoryId);
        }
        if (deviceId != null) {
            conditions.add("device_id = ?");
            arguments.add(deviceId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(work_order_no LIKE ? OR material_type LIKE ? OR process_type LIKE ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        appendWorkOrderScope(conditions, arguments);

        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false).orderBy("id", false);
        Page<BizWorkOrder> page = workOrderMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<WorkOrderView> records = buildViews(page.getRecords());
        return new PageResult<>(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize());
    }

    /** 工单详情；不存在或超出数据范围时抛异常。 */
    @Transactional(readOnly = true)
    public WorkOrderView get(long id) {
        BizWorkOrder workOrder = workOrderMapper.selectOneById(id);
        if (workOrder == null) {
            throw new BizException(404, "工单不存在：" + id);
        }
        requireWorkOrderAccess(workOrder);
        return buildView(workOrder);
    }

    /**
     * 查询某订单全部工单，供订单详情页展示排产进度。
     *
     * <p>先校验订单数据范围，再按订单号查询工单；订单号是订单分片键，但工单
     * 是单表，查询不会产生跨片广播。</p>
     */
    @Transactional(readOnly = true)
    public List<WorkOrderView> byOrder(long orderNo) {
        BizOrder order = orderMapper.selectOneById(orderNo);
        if (order == null) {
            throw new BizException(404, "订单不存在：" + orderNo);
        }
        requireOrderAccess(order);
        return buildViews(workOrderMapper.selectByOrderNo(orderNo));
    }

    /** 查询某设备活跃队列，供排产看板和插队服务复用。 */
    @Transactional(readOnly = true)
    public List<QueueEntryView> queueEntries(long deviceId) {
        BizDevice device = deviceService.requireAccessible(deviceId);
        return queueEntries(device);
    }

    /** 构建某设备活跃队列视图。 */
    public List<QueueEntryView> queueEntries(BizDevice device) {
        List<BizDeviceQueue> queues = queueMapper.selectActiveByDevice(device.getId());
        Map<Long, BizWorkOrder> workOrders = loadWorkOrders(queues.stream()
                .map(BizDeviceQueue::getWorkOrderId).collect(java.util.stream.Collectors.toSet()));
        List<QueueEntryView> entries = new ArrayList<>(queues.size());
        for (int i = 0; i < queues.size(); i++) {
            BizDeviceQueue queue = queues.get(i);
            BizWorkOrder workOrder = workOrders.get(queue.getWorkOrderId());
            if (workOrder != null) {
                entries.add(QueueEntryView.from(queue, workOrder, i + 1));
            }
        }
        return entries;
    }

    /** 读取工单并加锁，供状态变更使用。 */
    @Transactional
    public BizWorkOrder requireForUpdate(long id) {
        BizWorkOrder workOrder = workOrderMapper.selectForUpdate(id);
        if (workOrder == null) {
            throw new BizException(404, "工单不存在：" + id);
        }
        return workOrder;
    }

    /**
     * 重算设备队列计划时间。
     *
     * <p>正在生产的工单保留原计划；等待中的工单从当前时间或上一工单的预计
     * 完成时间开始顺延。每次插队、取消、完成都会调用，确保看板时间与队列
     * 顺序一致。计划时间只写 WAITING 记录，不影响历史 DONE/CANCELLED。</p>
     */
    @Transactional
    public void replanDeviceQueue(BizDevice device) {
        List<BizDeviceQueue> active = queueMapper.selectActiveForUpdate(device.getId());
        if (active.isEmpty()) {
            return;
        }
        Map<Long, BizWorkOrder> workOrders = loadWorkOrders(active.stream()
                .map(BizDeviceQueue::getWorkOrderId).collect(java.util.stream.Collectors.toSet()));
        BigDecimal capacity = device.getCapacityMetersPerHour();
        if (capacity == null || capacity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(409, "设备产能未配置，无法计算队列时间：" + device.getDeviceNo());
        }
        int changeover = device.getChangeoverMinutes() == null ? 0 : device.getChangeoverMinutes();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cursor = now;
        boolean runningSeen = false;
        for (BizDeviceQueue queue : active) {
            BizWorkOrder workOrder = workOrders.get(queue.getWorkOrderId());
            if (workOrder == null) {
                continue;
            }
            if ("RUNNING".equals(queue.getStatus())) {
                runningSeen = true;
                LocalDateTime end = queue.getPlannedEnd();
                if (end != null && end.isAfter(cursor)) {
                    cursor = end;
                }
                continue;
            }
            if (!"WAITING".equals(queue.getStatus())) {
                continue;
            }
            LocalDateTime start = runningSeen ? cursor : (cursor.isBefore(now) ? now : cursor);
            long minutes = durationMinutes(workOrder.getPlannedMeters(), capacity, changeover);
            LocalDateTime end = start.plusMinutes(minutes);
            queueMapper.updatePlan(queue.getId(), queue.getQueueSeq(), start, end, now);
            workOrder.setPlannedStart(start);
            workOrder.setPlannedEnd(end);
            workOrder.setUpdatedAt(now);
            workOrderMapper.update(workOrder);
            cursor = end;
        }
    }

    /**
     * 同步订单生产状态。
     *
     * <p>状态优先级：任一工单生产中 → IN_PRODUCTION；否则有排队工单 → QUEUED；
     * 全部完成 → COMPLETED；其余（全部取消或待排产）→ CREATED。已取消订单
     * 不再回写，避免把取消订单重新激活。</p>
     */
    @Transactional
    public void syncOrderStatus(long orderNo) {
        BizOrder order = orderMapper.selectOneById(orderNo);
        if (order == null || "CANCELLED".equals(order.getOrderStatus())) {
            return;
        }
        List<BizWorkOrder> workOrders = workOrderMapper.selectByOrderNo(orderNo);
        if (workOrders.isEmpty()) {
            return;
        }
        boolean anyRunning = workOrders.stream().anyMatch(item -> "RUNNING".equals(item.getStatus()));
        boolean anyQueued = workOrders.stream().anyMatch(item -> "QUEUED".equals(item.getStatus()));
        boolean allCompleted = workOrders.stream().allMatch(item -> "COMPLETED".equals(item.getStatus()));
        String target;
        if (anyRunning) {
            target = "IN_PRODUCTION";
        } else if (anyQueued) {
            target = "QUEUED";
        } else if (allCompleted) {
            target = "COMPLETED";
        } else {
            target = "CREATED";
        }
        if (!target.equals(order.getOrderStatus())) {
            order.setOrderStatus(target);
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.update(order);
        }
    }

    /**
     * 工单入队：追加到设备队尾，并重新计算预计时间。
     *
     * <p>正常排产每次增加 1000，给插队留出序号空间；插队审批会整体重排，
     * 不依赖这些间隙。入队后工单状态变为 QUEUED。重算会更新同一设备的所有
     * 等待工单计划时间，因此返回重新读取的工单，避免调用方用旧对象覆盖计划
     * 时间。</p>
     */
    private BizWorkOrder enqueue(BizWorkOrder workOrder, BizDevice device, LocalDateTime now) {
        Long maxSeq = queueMapper.selectMaxSeq(device.getId());
        long seq = (maxSeq == null ? 0L : maxSeq) + 1000L;
        BizDeviceQueue queue = new BizDeviceQueue();
        queue.setDeviceId(device.getId());
        queue.setWorkOrderId(workOrder.getId());
        queue.setQueueSeq(seq);
        queue.setStatus("WAITING");
        queue.setEnqueuedAt(now);
        queue.setOriginalSeq(seq);
        queue.setCreatedAt(now);
        queue.setUpdatedAt(now);
        queueMapper.insert(queue);

        workOrder.setDeviceId(device.getId());
        workOrder.setStatus("QUEUED");
        workOrder.setUpdatedAt(now);
        // 先落库设备与状态，重算时才能按当前队列找到该工单。
        workOrderMapper.update(workOrder);
        replanDeviceQueue(device);
        return workOrderMapper.selectOneById(workOrder.getId());
    }

    /** 校验设备是否可用于该工单：同工厂、状态可用、门幅/材料/工艺匹配。 */
    private void validateDeviceForWorkOrder(BizWorkOrder workOrder, BizDevice device) {
        requireFactoryAccessForOrder(device.getFactoryId());
        if (!workOrder.getFactoryId().equals(device.getFactoryId())) {
            throw new BizException(409, "设备与工单不属于同一工厂，禁止跨厂排产");
        }
        if (!SCHEDULABLE_DEVICE_STATUSES.contains(device.getStatus())) {
            throw new BizException(409, "设备当前状态不可排产：" + device.getStatus());
        }
        Integer width = workOrder.getFabricWidthMm();
        if (width != null && device.getMinWidthMm() != null && width < device.getMinWidthMm()) {
            throw new BizException(409, "门幅小于设备最小门幅：" + device.getMinWidthMm() + "mm");
        }
        if (width != null && device.getMaxWidthMm() != null && width > device.getMaxWidthMm()) {
            throw new BizException(409, "门幅超过设备最大门幅：" + device.getMaxWidthMm() + "mm");
        }
        if (!matchesToken(device.getMaterialTypes(), workOrder.getMaterialType())) {
            throw new BizException(409, "设备不支持该材料：" + workOrder.getMaterialType());
        }
        if (!matchesToken(device.getProcessTypes(), workOrder.getProcessType())) {
            throw new BizException(409, "设备不支持该工艺：" + workOrder.getProcessType());
        }
    }

    /**
     * 能力标签匹配：设备能力为空表示不限；否则忽略大小写比较，必须命中一个
     * 逗号分隔的标签。
     */
    private boolean matchesToken(String deviceTokens, String required) {
        if (deviceTokens == null || deviceTokens.isBlank()) {
            return true;
        }
        String target = required.trim();
        for (String token : deviceTokens.split("[,，;；]")) {
            if (token.trim().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /** 计算工单占用设备的分钟数：米数/产能×60 向上取整，再加换型时间。 */
    private long durationMinutes(BigDecimal meters, BigDecimal capacity, int changeover) {
        if (meters == null || meters.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "工单计划长度无效");
        }
        BigDecimal hours = meters.divide(capacity, 6, RoundingMode.HALF_UP);
        long production = hours.multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.CEILING).longValueExact();
        long total = Math.addExact(Math.max(1L, production), Math.max(0, changeover));
        // 单工单最长按 10 年计算，防止异常产能配置导致时间溢出。
        return Math.min(total, 5_256_000L);
    }

    /** 单条工单视图，补齐设备和队列。 */
    private WorkOrderView buildView(BizWorkOrder workOrder) {
        BizDevice device = workOrder.getDeviceId() == null ? null : deviceMapper.selectOneById(workOrder.getDeviceId());
        BizDeviceQueue queue = queueMapper.selectOneByQuery(QueryWrapper.create()
                .where("work_order_id = ?", workOrder.getId()));
        Integer position = null;
        if (queue != null && ("WAITING".equals(queue.getStatus()) || "RUNNING".equals(queue.getStatus()))) {
            position = positionOf(workOrder.getDeviceId(), workOrder.getId());
        }
        return WorkOrderView.from(workOrder, device, queue, position, materialCostOf(workOrder.getId()));
    }

    /** 批量构建工单视图，避免逐行查询设备和队列。 */
    private List<WorkOrderView> buildViews(List<BizWorkOrder> workOrders) {
        if (workOrders.isEmpty()) {
            return List.of();
        }
        Set<Long> workOrderIds = new HashSet<>();
        Set<Long> deviceIds = new HashSet<>();
        for (BizWorkOrder workOrder : workOrders) {
            workOrderIds.add(workOrder.getId());
            if (workOrder.getDeviceId() != null) {
                deviceIds.add(workOrder.getDeviceId());
            }
        }
        Map<Long, BizDevice> devices = loadDevices(deviceIds);
        Map<Long, BizDeviceQueue> queues = loadQueues(workOrderIds);
        Map<Long, Integer> positions = loadPositions(deviceIds);
        Map<Long, Long> materialCosts = materialCostsOf(workOrderIds);
        List<WorkOrderView> views = new ArrayList<>(workOrders.size());
        for (BizWorkOrder workOrder : workOrders) {
            BizDeviceQueue queue = queues.get(workOrder.getId());
            Integer position = queue == null ? null : positions.get(workOrder.getId());
            views.add(WorkOrderView.from(workOrder, devices.get(workOrder.getDeviceId()), queue, position,
                    materialCosts.get(workOrder.getId())));
        }
        return views;
    }

    /**
     * 工单材料成本（分）。
     *
     * <p>成本属于内部信息，只有拥有 {@code production:cost:read} 权限的角色可见；
     * 其他角色返回 null，避免通过工单列表泄露成本结构。</p>
     */
    private Long materialCostOf(long workOrderId) {
        if (!SecurityUtils.hasPermission(COST_READ_PERMISSION)) {
            return null;
        }
        return materialIssueService.materialCostOf(workOrderId);
    }

    /**
     * 批量计算工单材料成本（分）。
     *
     * <p>没有成本查看权限时直接返回空 Map，避免逐单查询。</p>
     */
    private Map<Long, Long> materialCostsOf(Set<Long> workOrderIds) {
        if (!SecurityUtils.hasPermission(COST_READ_PERMISSION)) {
            return Map.of();
        }
        return materialIssueService.materialCostsOf(workOrderIds);
    }

    /** 加载设备 Map。 */
    private Map<Long, BizDevice> loadDevices(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<BizDevice> list = deviceMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
        Map<Long, BizDevice> result = new HashMap<>();
        for (BizDevice device : list) {
            result.put(device.getId(), device);
        }
        return result;
    }

    /** 加载工单 Map。 */
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

    /** 加载工单的队列记录（含历史），用于详情视图。 */
    private Map<Long, BizDeviceQueue> loadQueues(Set<Long> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        List<BizDeviceQueue> list = queueMapper.selectListByQuery(QueryWrapper.create()
                .where("work_order_id IN (" + placeholders(workOrderIds.size()) + ")", workOrderIds.toArray()));
        Map<Long, BizDeviceQueue> result = new HashMap<>();
        for (BizDeviceQueue queue : list) {
            result.put(queue.getWorkOrderId(), queue);
        }
        return result;
    }

    /** 批量计算活跃队列位置：同一设备按 queueSeq 排序，位置从 1 开始。 */
    private Map<Long, Integer> loadPositions(Set<Long> deviceIds) {
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(deviceIds);
        List<BizDeviceQueue> queues = queueMapper.selectActiveByDeviceIds(ids);
        Map<Long, Integer> positions = new HashMap<>();
        Map<Long, Integer> counters = new HashMap<>();
        for (BizDeviceQueue queue : queues) {
            int next = counters.getOrDefault(queue.getDeviceId(), 0) + 1;
            counters.put(queue.getDeviceId(), next);
            positions.put(queue.getWorkOrderId(), next);
        }
        return positions;
    }

    /** 查询某工单在当前设备队列中的位置；不在活跃队列返回 null。 */
    private Integer positionOf(Long deviceId, Long workOrderId) {
        if (deviceId == null) {
            return null;
        }
        List<BizDeviceQueue> active = queueMapper.selectActiveByDevice(deviceId);
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).getWorkOrderId().equals(workOrderId)) {
                return i + 1;
            }
        }
        return null;
    }

    /** 从活跃队列中找到指定工单的队列记录。 */
    private BizDeviceQueue findQueue(List<BizDeviceQueue> active, Long workOrderId) {
        for (BizDeviceQueue queue : active) {
            if (queue.getWorkOrderId().equals(workOrderId)) {
                return queue;
            }
        }
        return null;
    }

    /** 追加工单数据范围：销售按业务员，厂长按工厂，管理员/财务/机器令牌全组织。 */
    private void appendWorkOrderScope(List<String> conditions, List<Object> arguments) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            Long factoryId = SecurityUtils.factoryId();
            conditions.add("factory_id = ?");
            arguments.add(factoryId == null ? -1L : factoryId);
            return;
        }
        Long userId = SecurityUtils.userId();
        conditions.add("sales_user_id = ?");
        arguments.add(userId == null ? -1L : userId);
    }

    /** 单条工单数据范围判断。 */
    private void requireWorkOrderAccess(BizWorkOrder workOrder) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            if (!java.util.Objects.equals(workOrder.getFactoryId(), SecurityUtils.factoryId())) {
                throw new BizException(403, "无权查看其他工厂的工单");
            }
            return;
        }
        if (!java.util.Objects.equals(workOrder.getSalesUserId(), SecurityUtils.userId())) {
            throw new BizException(403, "无权查看其他业务员的工单");
        }
    }

    /** 订单数据范围判断；排产人员必须是管理员、机器令牌或订单所属工厂厂长。 */
    private void requireOrderAccess(BizOrder order) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            if (!java.util.Objects.equals(order.getFactoryId(), SecurityUtils.factoryId())) {
                throw new BizException(403, "无权查看其他工厂的订单");
            }
            return;
        }
        if (!java.util.Objects.equals(order.getCreatedBy(), SecurityUtils.userId())) {
            throw new BizException(403, "无权查看其他业务员的订单");
        }
    }

    /** 排产写入的工厂范围：厂长只能排本厂，管理员/机器令牌不受限。 */
    private void requireFactoryAccessForOrder(Long factoryId) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            if (!java.util.Objects.equals(factoryId, SecurityUtils.factoryId())) {
                throw new BizException(403, "只能排产本厂订单");
            }
            return;
        }
        throw new BizException(403, "无排产权限");
    }

    /** 生成 IN 子句占位符。 */
    private static String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
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
