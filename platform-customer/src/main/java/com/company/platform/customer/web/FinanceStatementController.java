package com.company.platform.customer.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.customer.service.StatementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 客户对账单接口。
 *
 * <p>对账单按客户和期间生成，保存期初、本期应收、本期核销收款、本期开票和
 * 期末快照；客户确认后归档。路径沿用 {@code /api/finance/**}，当前由客户
 * 服务托管。</p>
 *
 * <p>权限：查看需要 {@code finance:statement:read}，生成和确认需要
 * {@code finance:statement:write}。</p>
 */
@RestController
@RequestMapping("/api/finance/statements")
public class FinanceStatementController {

    private final StatementService statementService;

    public FinanceStatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    /**
     * 对账单分页。
     *
     * @param page       页码
     * @param size       每页条数，最大 100
     * @param keyword    客户名称/编码或系统单号
     * @param customerId 客户 ID
     * @param status     GENERATED/CONFIRMED/CANCELLED
     * @param periodFrom 期间筛选起始日（按区间交集）
     * @param periodTo   期间筛选结束日（按区间交集）
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:statement:read')")
    public ApiResponse<PageResult<StatementView>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo) {
        return ApiResponse.ok(statementService.page(page, size, keyword, customerId, status, periodFrom, periodTo));
    }

    /** 对账单详情，包含快照明细。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:statement:read')")
    public ApiResponse<StatementView> get(@PathVariable Long id) {
        return ApiResponse.ok(statementService.get(id));
    }

    /** 生成对账单。 */
    @PostMapping
    @PreAuthorize("hasAuthority('finance:statement:write')")
    public ApiResponse<StatementView> generate(@Valid @RequestBody StatementGenerateCommand command) {
        return ApiResponse.ok(statementService.generate(command));
    }

    /** 客户确认对账单，可记录差异说明。 */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('finance:statement:write')")
    public ApiResponse<StatementView> confirm(@PathVariable Long id,
                                              @Valid @RequestBody(required = false) StatementConfirmCommand command) {
        return ApiResponse.ok(statementService.confirm(id, command));
    }
}
