package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizDeviceQueue;
import com.company.platform.order.entity.BizStationMessage;
import com.company.platform.order.entity.BizWorkOrder;
import com.company.platform.order.mapper.BizStationMessageMapper;
import com.company.platform.order.mapper.BizWorkOrderMapper;
import com.company.platform.order.web.MessagePageView;
import com.company.platform.order.web.StationMessageView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 站内消息服务。
 *
 * <p>首期只用于设备队列插队通知：审批通过后被顺延工单的业务员会收到消息，
 * 申请人收到审批结果；设备停机且仍有等待队列时，也会提醒相关业务员交期
 * 可能顺延。消息只允许接收人本人读取和标记已读，不提供物理删除。</p>
 */
@Service
public class StationMessageService {

    /** 队列被顺延。 */
    public static final String TYPE_QUEUE_DISPLACED = "QUEUE_DISPLACED";
    /** 插队审批结果。 */
    public static final String TYPE_QUEUE_JUMP_RESULT = "QUEUE_JUMP_RESULT";
    /** 系统提醒，例如设备停机影响排队。 */
    public static final String TYPE_SYSTEM = "SYSTEM";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BizStationMessageMapper messageMapper;
    private final BizWorkOrderMapper workOrderMapper;

    public StationMessageService(BizStationMessageMapper messageMapper, BizWorkOrderMapper workOrderMapper) {
        this.messageMapper = messageMapper;
        this.workOrderMapper = workOrderMapper;
    }

    /**
     * 发送一条站内消息。
     *
     * <p>接收人为空时直接跳过，避免给不存在的业务员写入孤儿消息。标题和内容
     * 会按数据库列长度截断，防止插队原因过长导致整个审批事务回滚。</p>
     */
    @Transactional
    public void send(Long userId, String messageType, String title, String content,
                     String bizType, String bizId, String linkPath) {
        if (userId == null) {
            return;
        }
        BizStationMessage message = new BizStationMessage();
        message.setUserId(userId);
        message.setMessageType(messageType);
        message.setTitle(truncate(title, 200));
        message.setContent(truncate(content, 1000));
        message.setBizType(truncate(bizType, 32));
        message.setBizId(truncate(bizId, 64));
        message.setLinkPath(truncate(linkPath, 255));
        message.setReadFlag(0);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    /**
     * 设备停机且仍有等待队列时提醒业务员。
     *
     * <p>消息按设备聚合发送给每条队列工单的业务员，内容包含设备、工单、订单
     * 和目标状态。当前不做交期自动重算，只提示风险，待保养/维修停机时长
     * 模块落地后再计算新的预计完成时间。</p>
     */
    @Transactional
    public void notifyDeviceUnavailable(BizDevice device, String targetStatus,
                                        List<BizDeviceQueue> queues, String reason) {
        String statusLabel = statusLabel(targetStatus);
        for (BizDeviceQueue queue : queues) {
            BizWorkOrder workOrder = workOrderMapper.selectOneById(queue.getWorkOrderId());
            if (workOrder == null || workOrder.getSalesUserId() == null) {
                continue;
            }
            String content = "设备 " + device.getDeviceNo() + "（" + device.getDeviceName() + "）已转为"
                    + statusLabel + "，工单 " + workOrder.getWorkOrderNo() + "（订单 "
                    + workOrder.getOrderNo() + "）仍在队列中，开始时间可能顺延。"
                    + (reason == null || reason.isBlank() ? "" : "原因：" + reason.trim());
            send(workOrder.getSalesUserId(), TYPE_SYSTEM, "设备停机，排队工单受影响",
                    content, "WORK_ORDER", String.valueOf(workOrder.getId()),
                    "/orders/" + workOrder.getOrderNo());
        }
    }

    /**
     * 设备恢复生产时通知等待队列上的业务员。
     *
     * <p>维护工单完成后调用：队列顺序不变，但计划时间会按新的空闲时间重算，
     * 因此提醒业务员关注新的预计开始时间。</p>
     */
    @Transactional
    public void notifyDeviceRestored(BizDevice device, List<BizDeviceQueue> queues, String maintenanceNo) {
        for (BizDeviceQueue queue : queues) {
            BizWorkOrder workOrder = workOrderMapper.selectOneById(queue.getWorkOrderId());
            if (workOrder == null || workOrder.getSalesUserId() == null) {
                continue;
            }
            String content = "设备 " + device.getDeviceNo() + "（" + device.getDeviceName() + "）维护完成已恢复空闲，"
                    + "工单 " + workOrder.getWorkOrderNo() + "（订单 " + workOrder.getOrderNo()
                    + "）仍在队列中，预计时间已重新计算。"
                    + (maintenanceNo == null || maintenanceNo.isBlank() ? "" : "维护工单：" + maintenanceNo.trim());
            send(workOrder.getSalesUserId(), TYPE_SYSTEM, "设备恢复生产",
                    content, "WORK_ORDER", String.valueOf(workOrder.getId()),
                    "/orders/" + workOrder.getOrderNo());
        }
    }

    /** 当前用户未读消息数；没有登录用户时返回 0。 */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        return messageMapper.selectCountByQuery(QueryWrapper.create()
                .where("user_id = ? AND read_flag = 0", userId));
    }

    /** 当前用户消息分页，可按未读筛选。 */
    @Transactional(readOnly = true)
    public MessagePageView page(Long userId, long pageNumber, long pageSize, boolean unreadOnly) {
        if (userId == null) {
            return new MessagePageView(List.of(), 0, pageNumber, pageSize, 0);
        }
        QueryWrapper query = QueryWrapper.create().where("user_id = ?", userId);
        if (unreadOnly) {
            query.and("read_flag = 0");
        }
        query.orderBy("created_at", false).orderBy("id", false);
        Page<BizStationMessage> page = messageMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<StationMessageView> records = page.getRecords().stream().map(StationMessageView::from).toList();
        return new MessagePageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(),
                unreadCount(userId));
    }

    /**
     * 标记一条消息已读。
     *
     * <p>只允许接收人本人操作；消息不存在或不属于当前用户统一返回 404，避免
     * 暴露他人消息 ID。</p>
     */
    @Transactional
    public StationMessageView markRead(long id, Long userId) {
        if (userId == null) {
            throw new BizException(401, "Unauthorized");
        }
        BizStationMessage message = messageMapper.selectOneById(id);
        if (message == null || !userId.equals(message.getUserId())) {
            throw new BizException(404, "消息不存在：" + id);
        }
        if (message.getReadFlag() == null || message.getReadFlag() == 0) {
            messageMapper.markRead(id, userId, LocalDateTime.now());
            message = messageMapper.selectOneById(id);
        }
        return StationMessageView.from(message);
    }

    /** 当前用户全部消息标记已读，返回本次更新条数。 */
    @Transactional
    public int markAllRead(Long userId) {
        if (userId == null) {
            throw new BizException(401, "Unauthorized");
        }
        return messageMapper.markAllRead(userId, LocalDateTime.now());
    }

    /** 状态中文名称，用于消息正文。 */
    private static String statusLabel(String status) {
        return Map.of(
                "MAINTENANCE", "保养中",
                "FAULT", "故障维修",
                "DISABLED", "停用"
        ).getOrDefault(status, status);
    }

    /** 按列长度截断文本，null 原样返回。 */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
