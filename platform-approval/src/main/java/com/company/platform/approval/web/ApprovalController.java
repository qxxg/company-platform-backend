package com.company.platform.approval.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.approval.service.ApprovalService;
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
 * 审批中心接口。
 *
 * <p>提交申请需要 {@code approval:submit}，查看审批单需要 {@code approval:read}，
 * 审批动作需要 {@code approval:approve}。</p>
 */
@RestController
@RequestMapping("/api/approval")
@Validated
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /** 健康检查，便于网关和运维探活。 */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("ok");
    }

    /**
     * 提交审批申请。
     *
     * <p>按业务类型与金额匹配审批档位；没有配置审批流程的业务类型不允许提交。</p>
     */
    @PostMapping("/instances")
    @PreAuthorize("hasAuthority('approval:submit')")
    public ApiResponse<ApprovalView> submit(@Valid @RequestBody ApprovalSubmitCommand command) {
        return ApiResponse.ok(approvalService.submit(command));
    }

    /**
     * 审批单分页。
     *
     * @param page        页码
     * @param size        每页条数
     * @param businessType 业务类型过滤
     * @param status      状态过滤
     * @param mine        只看本人提交的申请
     */
    @GetMapping("/instances/page")
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<ApprovalPageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mine) {
        return ApiResponse.ok(approvalService.page(page, size, businessType, status, mine));
    }

    /** 我的待办分页：按指定审批人或审批角色命中。 */
    @GetMapping("/todos/page")
    @PreAuthorize("hasAuthority('approval:approve')")
    public ApiResponse<ApprovalPageView> todos(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String businessType) {
        return ApiResponse.ok(approvalService.todo(page, size, businessType));
    }

    /** 审批单详情（含任务与轨迹）。 */
    @GetMapping("/instances/{id}")
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<ApprovalView> get(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.get(id));
    }

    /** 审批同意：业务动作在结论落库前执行，失败整体回滚。 */
    @PostMapping("/tasks/{taskId}/approve")
    @PreAuthorize("hasAuthority('approval:approve')")
    public ApiResponse<ApprovalView> approve(@PathVariable Long taskId,
                                             @RequestBody(required = false) ApprovalDecisionCommand command) {
        return ApiResponse.ok(approvalService.approve(taskId, command == null ? null : command.comment()));
    }

    /** 审批驳回：必须填写意见。 */
    @PostMapping("/tasks/{taskId}/reject")
    @PreAuthorize("hasAuthority('approval:approve')")
    public ApiResponse<ApprovalView> reject(@PathVariable Long taskId,
                                            @Valid @RequestBody ApprovalDecisionCommand command) {
        return ApiResponse.ok(approvalService.reject(taskId, command.comment()));
    }

    /** 申请人撤销待审批申请。 */
    @PostMapping("/instances/{id}/cancel")
    @PreAuthorize("hasAuthority('approval:submit')")
    public ApiResponse<ApprovalView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.cancel(id));
    }
}
