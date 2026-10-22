package com.company.platform.order.service;

import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizDeviceQueue;
import com.company.platform.order.entity.BizWorkOrder;
import com.company.platform.order.mapper.BizDeviceMapper;
import com.company.platform.order.mapper.BizDeviceQueueMapper;
import com.company.platform.order.mapper.BizQueueJumpRequestMapper;
import com.company.platform.order.mapper.BizWorkOrderMapper;
import com.company.platform.order.web.DeviceQueueView;
import com.company.platform.order.web.DeviceView;
import com.company.platform.order.web.ProductionBoardView;
import com.company.platform.order.web.ProductionSummaryView;
import com.company.platform.order.web.QueueEntryView;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备队列看板服务。
 *
 * <p>按工厂/设备展示当前生产工单、等待队列、队列位置和预计开始/完成时间。
 * 看板只读，不修改队列；开始、完成、取消和插队分别走工单/插队服务。</p>
 *
 * <p>数据范围：厂长只看本厂；管理员、财务和机器令牌看全部；销售可以查看
 * 全部工厂的设备队列，用于跟踪自己订单的排产进度。设备台账本身仍由
 * {@code equipment:read} 控制。</p>
 */
@Service
public class ProductionBoardService {

    private final BizDeviceMapper deviceMapper;
    private final BizDeviceQueueMapper queueMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizQueueJumpRequestMapper jumpRequestMapper;

    public ProductionBoardService(BizDeviceMapper deviceMapper,
                                  BizDeviceQueueMapper queueMapper,
                                  BizWorkOrderMapper workOrderMapper,
                                  BizQueueJumpRequestMapper jumpRequestMapper) {
        this.deviceMapper = deviceMapper;
        this.queueMapper = queueMapper;
        this.workOrderMapper = workOrderMapper;
        this.jumpRequestMapper = jumpRequestMapper;
    }

    /**
     * 构建排产看板。
     *
     * @param factoryId 可选工厂筛选；厂长会被强制限制到本厂
     */
    @Transactional(readOnly = true)
    public ProductionBoardView board(Long factoryId) {
        Long effectiveFactoryId = effectiveFactory(factoryId);
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (effectiveFactoryId != null) {
            conditions.add("factory_id = ?");
            arguments.add(effectiveFactoryId);
        }
        // 停用设备不在生产看板中展示，但设备台账仍保留。
        conditions.add("status <> 'DISABLED'");
        QueryWrapper query = QueryWrapper.create()
                .where(String.join(" AND ", conditions), arguments.toArray())
                .orderBy("factory_id", true)
                .orderBy("device_no", true);
        List<BizDevice> devices = deviceMapper.selectListByQuery(query);
        if (devices.isEmpty()) {
            return new ProductionBoardView(effectiveFactoryId, List.of(),
                    new ProductionSummaryView(0, 0, 0, 0, 0, 0, 0, 0));
        }

        List<Long> deviceIds = devices.stream().map(BizDevice::getId).toList();
        List<BizDeviceQueue> queues = queueMapper.selectActiveByDeviceIds(deviceIds);
        Set<Long> workOrderIds = new HashSet<>();
        for (BizDeviceQueue queue : queues) {
            workOrderIds.add(queue.getWorkOrderId());
        }
        Map<Long, BizWorkOrder> workOrders = loadWorkOrders(workOrderIds);

        Map<Long, List<BizDeviceQueue>> queueMap = new HashMap<>();
        for (BizDeviceQueue queue : queues) {
            queueMap.computeIfAbsent(queue.getDeviceId(), key -> new ArrayList<>()).add(queue);
        }

        // 销售只能看到自己订单产生的队列记录，避免通过排产看板读取其他业务员
        // 的客户和订单信息；管理员、财务和厂长按原有范围查看完整队列。
        boolean salesScoped = isSalesScoped();
        Long currentUserId = SecurityUtils.userId();
        long idle = 0;
        long running = 0;
        long maintenance = 0;
        long fault = 0;
        long waitingCount = 0;
        long runningWorkOrders = 0;
        List<DeviceQueueView> boardDevices = new ArrayList<>(devices.size());
        for (BizDevice device : devices) {
            switch (device.getStatus()) {
                case "IDLE" -> idle++;
                case "RUNNING" -> running++;
                case "MAINTENANCE" -> maintenance++;
                case "FAULT" -> fault++;
                default -> {
                    // 未知状态不计入汇总。
                }
            }
            List<BizDeviceQueue> deviceQueues = queueMap.getOrDefault(device.getId(), List.of());
            List<QueueEntryView> allEntries = new ArrayList<>(deviceQueues.size());
            for (int i = 0; i < deviceQueues.size(); i++) {
                BizDeviceQueue queue = deviceQueues.get(i);
                BizWorkOrder workOrder = workOrders.get(queue.getWorkOrderId());
                if (workOrder == null) {
                    continue;
                }
                // 位置按完整队列计算，销售过滤后仍能看到自己在真实队列中的位置。
                allEntries.add(QueueEntryView.from(queue, workOrder, i + 1));
            }
            List<QueueEntryView> entries = allEntries;
            if (salesScoped) {
                entries = allEntries.stream()
                        .filter(entry -> java.util.Objects.equals(entry.salesUserId(), currentUserId))
                        .toList();
            }
            QueueEntryView current = null;
            for (QueueEntryView entry : entries) {
                if ("RUNNING".equals(entry.queueStatus())) {
                    current = entry;
                    runningWorkOrders++;
                }
                if ("WAITING".equals(entry.queueStatus())) {
                    waitingCount++;
                }
            }
            BizWorkOrder currentWorkOrder = current == null ? null
                    : workOrders.get(current.workOrderId());
            LocalDateTime nextFreeAt = entries.isEmpty()
                    ? null : entries.get(entries.size() - 1).plannedEnd();
            DeviceView deviceView = DeviceView.from(device,
                    currentWorkOrder == null ? null : currentWorkOrder.getWorkOrderNo(),
                    currentWorkOrder == null ? null : currentWorkOrder.getOrderNo(),
                    entries.size(), nextFreeAt);
            boardDevices.add(new DeviceQueueView(deviceView, entries, current));
        }

        long pendingJumpCount;
        QueryWrapper jumpQuery = QueryWrapper.create().where("status = 'PENDING'");
        if (effectiveFactoryId != null) {
            jumpQuery.and("factory_id = ?", effectiveFactoryId);
        }
        if (salesScoped) {
            // 销售只统计自己发起的待审批申请，不暴露同事的申请数量。
            jumpQuery.and("requested_by = ?", currentUserId == null ? -1L : currentUserId);
        }
        pendingJumpCount = jumpRequestMapper.selectCountByQuery(jumpQuery);

        ProductionSummaryView summary = new ProductionSummaryView(devices.size(), idle, running,
                maintenance, fault, waitingCount, runningWorkOrders, pendingJumpCount);
        return new ProductionBoardView(effectiveFactoryId, boardDevices, summary);
    }

    /** 厂长强制本厂；其他有看板权限的角色可以按传入工厂筛选或查看全部。 */
    private Long effectiveFactory(Long factoryId) {
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            return SecurityUtils.factoryId();
        }
        return factoryId;
    }

    /**
     * 是否按业务员过滤队列。
     *
     * <p>管理员、财务、厂长和机器令牌可以看完整队列；销售/操作员等普通用户
     * 只能看自己订单对应的工单，避免通过看板读取其他业务员的客户和订单。</p>
     */
    private boolean isSalesScoped() {
        return !SecurityUtils.isServiceAccount()
                && !SecurityUtils.hasAnyRole("ADMIN", "FINANCE", "FACTORY_MANAGER");
    }

    /** 批量加载看板涉及的工单。 */
    private Map<Long, BizWorkOrder> loadWorkOrders(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<BizWorkOrder> list = workOrderMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")",
                        ids.toArray()));
        Map<Long, BizWorkOrder> result = new HashMap<>();
        for (BizWorkOrder workOrder : list) {
            result.put(workOrder.getId(), workOrder);
        }
        return result;
    }
}
