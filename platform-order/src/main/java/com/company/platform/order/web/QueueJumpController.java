package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.service.QueueJumpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备队列插队申请接口。
 *
 * <p>审批已迁入统一审批中心：销售对排队中的工单发起申请后，申请单同步提交
 * 审批中心，由厂长在「我的待办」中处理。审批通过、驳回或撤销时，审批中心回调
 * 本服务的三个同步接口执行队列重排与状态同步。</p>
 *
 * <p>权限：申请需要 {@code production:queue:jump:apply}；查看申请需要申请或
 * 审批权限；审批回调分别需要 {@code approval:apply}（通过并重排）、
 * {@code approval:approve}（驳回）与 {@code approval:submit}（撤销）。</p>
 */
@RestController
@RequestMapping("/api/order")
@Validated
public class QueueJumpController {

    private final QueueJumpService queueJumpService;

    public QueueJumpController(QueueJumpService queueJumpService) {
        this.queueJumpService = queueJumpService;
    }

    /** 销售对排队中工单发起插队申请，同时提交审批中心。 */
    @PostMapping("/work-orders/{workOrderId}/jump-requests")
    @PreAuthorize("hasAuthority('production:queue:jump:apply')")
    public ApiResponse<JumpRequestView> create(@PathVariable Long workOrderId,
                                               @Valid @RequestBody QueueJumpCreateCommand command) {
        return ApiResponse.ok(queueJumpService.create(workOrderId, command, SecurityUtils.userId()));
    }

    /**
     * 插队申请分页。
     *
     * @param page      页码
     * @param size      每页条数，最大 100
     * @param status    PENDING/APPROVED/REJECTED/CANCELLED
     * @param factoryId 工厂 ID
     */
    @GetMapping("/jump-requests/page")
    @PreAuthorize("hasAnyAuthority('production:queue:jump:apply','production:queue:jump:approve')")
    public ApiResponse<PageResult<JumpRequestView>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(queueJumpService.page(page, size, status, factoryId));
    }

    /** 申请详情。 */
    @GetMapping("/jump-requests/{id}")
    @PreAuthorize("hasAnyAuthority('production:queue:jump:apply','production:queue:jump:approve')")
    public ApiResponse<JumpRequestView> get(@PathVariable Long id) {
        return ApiResponse.ok(queueJumpService.get(id));
    }

    /**
     * 审批中心回调：审批通过后重排队列并发送站内消息。
     *
     * <p>由审批中心携带审批人身份调用；队列重排与申请状态在同一个事务内完成。</p>
     */
    @PostMapping("/jump-requests/{id}/apply-approval")
    @PreAuthorize("hasAuthority('approval:apply')")
    public ApiResponse<JumpRequestView> applyApproval(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false)
                                                       QueueJumpApprovalCommand command) {
        return ApiResponse.ok(queueJumpService.applyApproved(id, command));
    }

    /** 审批中心回调：审批驳回，同步申请状态并通知申请人。 */
    @PostMapping("/jump-requests/{id}/reject-approval")
    @PreAuthorize("hasAuthority('approval:approve')")
    public ApiResponse<JumpRequestView> rejectApproval(@PathVariable Long id,
                                                      @Valid @RequestBody QueueJumpApprovalCommand command) {
        return ApiResponse.ok(queueJumpService.markRejected(id, command));
    }

    /** 审批中心回调：申请人撤销，同步申请状态。 */
    @PostMapping("/jump-requests/{id}/cancel-approval")
    @PreAuthorize("hasAuthority('approval:submit')")
    public ApiResponse<JumpRequestView> cancelApproval(@PathVariable Long id) {
        return ApiResponse.ok(queueJumpService.markCancelled(id));
    }
}
