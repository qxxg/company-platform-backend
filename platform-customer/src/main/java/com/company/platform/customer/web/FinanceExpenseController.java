package com.company.platform.customer.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.customer.service.ExpenseClaimService;
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
import java.util.List;

/**
 * 费用报销接口。
 *
 * <p>路径统一为 {@code /api/finance/expenses/**}，由客户服务托管，与应收、
 * 合同共用 {@code company_platform} 库。审批通过/驳回/撤销的回调接口要求
 * {@code approval:apply}，只允许审批中心带着审批人身份调用。</p>
 */
@RestController
@RequestMapping("/api/finance/expenses")
@Validated
public class FinanceExpenseController {

    private final ExpenseClaimService expenseClaimService;

    public FinanceExpenseController(ExpenseClaimService expenseClaimService) {
        this.expenseClaimService = expenseClaimService;
    }

    /** 报销单分页。 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:expense:read')")
    public ApiResponse<ExpenseClaimPageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(expenseClaimService.page(page, size, keyword, status, expenseType, dateFrom, dateTo));
    }

    /** 报销单详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:expense:read')")
    public ApiResponse<ExpenseClaimView> get(@PathVariable Long id) {
        return ApiResponse.ok(expenseClaimService.get(id));
    }

    /** 新建报销草稿。 */
    @PostMapping
    @PreAuthorize("hasAuthority('finance:expense:write')")
    public ApiResponse<ExpenseClaimView> create(@Valid @RequestBody ExpenseClaimCreateCommand command) {
        return ApiResponse.ok(expenseClaimService.create(command));
    }

    /** 提交审批。 */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('finance:expense:write')")
    public ApiResponse<ExpenseClaimView> submit(@PathVariable Long id) {
        return ApiResponse.ok(expenseClaimService.submit(id));
    }

    /** 取消草稿或撤销审批中的申请。 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('finance:expense:write')")
    public ApiResponse<ExpenseClaimView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(expenseClaimService.cancel(id));
    }

    /** 财务登记付款：支持现金/转账，以及用未还借款冲销。 */
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('finance:expense:pay')")
    public ApiResponse<ExpenseClaimView> pay(@PathVariable Long id,
                                             @Valid @RequestBody ExpenseClaimPayCommand command) {
        return ApiResponse.ok(expenseClaimService.pay(id, command));
    }

    /** 报销人的未还借款，供付款冲销选择。 */
    @GetMapping("/{id}/outstanding-loans")
    @PreAuthorize("hasAuthority('finance:expense:pay')")
    public ApiResponse<List<OutstandingLoanView>> outstandingLoans(@PathVariable Long id) {
        return ApiResponse.ok(expenseClaimService.outstandingLoans(id));
    }

    /** 审批通过回调：审批中 → 待付款。 */
    @PostMapping("/{id}/approval/approved")
    @PreAuthorize("hasAuthority('approval:apply')")
    public ApiResponse<ExpenseClaimView> approved(@PathVariable Long id,
                                                  @RequestBody(required = false) ApprovalNoteCommand command) {
        return ApiResponse.ok(expenseClaimService.markApproved(id, command == null ? null : command.comment()));
    }

    /** 审批驳回回调：审批中 → 已驳回。 */
    @PostMapping("/{id}/approval/rejected")
    @PreAuthorize("hasAuthority('approval:apply')")
    public ApiResponse<ExpenseClaimView> rejected(@PathVariable Long id,
                                                  @RequestBody(required = false) ApprovalNoteCommand command) {
        return ApiResponse.ok(expenseClaimService.markRejected(id, command == null ? null : command.comment()));
    }

    /** 申请人撤销回调：审批中 → 已取消。 */
    @PostMapping("/{id}/approval/cancelled")
    @PreAuthorize("hasAnyAuthority('approval:apply', 'approval:submit')")
    public ApiResponse<ExpenseClaimView> cancelled(@PathVariable Long id) {
        return ApiResponse.ok(expenseClaimService.markCancelled(id));
    }
}
