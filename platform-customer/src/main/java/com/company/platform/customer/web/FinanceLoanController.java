package com.company.platform.customer.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.customer.service.LoanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 借款与还款接口。
 *
 * <p>路径统一为 {@code /api/finance/loans/**}。借款与报销是独立单据：
 * 借款审批通过后还要财务放款，还款可以手工登记，也可以在报销付款时冲销。
 * 审批结论回调只允许审批中心携带审批人身份调用。</p>
 */
@RestController
@RequestMapping("/api/finance/loans")
@Validated
public class FinanceLoanController {

    private final LoanService loanService;

    public FinanceLoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    /** 借款单分页。 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:loan:read')")
    public ApiResponse<LoanPageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(loanService.page(page, size, keyword, status, dateFrom, dateTo));
    }

    /** 借款单详情，包含还款记录。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:loan:read')")
    public ApiResponse<LoanView> get(@PathVariable Long id) {
        return ApiResponse.ok(loanService.get(id));
    }

    /** 新建借款草稿。 */
    @PostMapping
    @PreAuthorize("hasAuthority('finance:loan:write')")
    public ApiResponse<LoanView> create(@Valid @RequestBody LoanCreateCommand command) {
        return ApiResponse.ok(loanService.create(command));
    }

    /** 提交审批。 */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('finance:loan:write')")
    public ApiResponse<LoanView> submit(@PathVariable Long id) {
        return ApiResponse.ok(loanService.submit(id));
    }

    /** 取消草稿或撤销审批中的申请。 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('finance:loan:write')")
    public ApiResponse<LoanView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(loanService.cancel(id));
    }

    /** 财务登记放款。 */
    @PostMapping("/{id}/disburse")
    @PreAuthorize("hasAuthority('finance:loan:disburse')")
    public ApiResponse<LoanView> disburse(@PathVariable Long id,
                                          @Valid @RequestBody LoanDisburseCommand command) {
        return ApiResponse.ok(loanService.disburse(id, command));
    }

    /** 财务登记手工还款。 */
    @PostMapping("/{id}/repay")
    @PreAuthorize("hasAuthority('finance:loan:repay')")
    public ApiResponse<LoanView> repay(@PathVariable Long id,
                                       @Valid @RequestBody LoanRepayCommand command) {
        return ApiResponse.ok(loanService.repay(id, command));
    }

    /** 审批通过回调：审批中 → 待放款。 */
    @PostMapping("/{id}/approval/approved")
    @PreAuthorize("hasAuthority('approval:apply')")
    public ApiResponse<LoanView> approved(@PathVariable Long id,
                                          @RequestBody(required = false) ApprovalNoteCommand command) {
        return ApiResponse.ok(loanService.markApproved(id, command == null ? null : command.comment()));
    }

    /** 审批驳回回调：审批中 → 已驳回。 */
    @PostMapping("/{id}/approval/rejected")
    @PreAuthorize("hasAuthority('approval:apply')")
    public ApiResponse<LoanView> rejected(@PathVariable Long id,
                                          @RequestBody(required = false) ApprovalNoteCommand command) {
        return ApiResponse.ok(loanService.markRejected(id, command == null ? null : command.comment()));
    }

    /** 申请人撤销回调：审批中 → 已取消。 */
    @PostMapping("/{id}/approval/cancelled")
    @PreAuthorize("hasAnyAuthority('approval:apply', 'approval:submit')")
    public ApiResponse<LoanView> cancelled(@PathVariable Long id) {
        return ApiResponse.ok(loanService.markCancelled(id));
    }
}
