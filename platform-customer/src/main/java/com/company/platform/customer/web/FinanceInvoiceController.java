package com.company.platform.customer.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.customer.service.InvoiceService;
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
 * 财务手工开票接口。
 *
 * <p>路径沿用 {@code /api/finance/**}，当前由客户服务托管；只做内部开票登记，
 * 不对接税控或电子发票平台。作废/红冲必须关联原发票并走审批，审批中心上线前
 * 不提供接口。</p>
 *
 * <p>权限：查看需要 {@code finance:invoice:read}，登记和状态推进需要
 * {@code finance:invoice:write}。</p>
 */
@RestController
@RequestMapping("/api/finance/invoices")
public class FinanceInvoiceController {

    private final InvoiceService invoiceService;

    public FinanceInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * 发票分页。
     *
     * @param page       页码
     * @param size       每页条数，最大 100
     * @param keyword    客户名称/编码、发票号码、抬头或系统单号
     * @param customerId 客户 ID
     * @param status     REGISTERED/SENT/SIGNED/VOID/REVERSED
     * @param dateFrom   开票日起
     * @param dateTo     开票日止
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:invoice:read')")
    public ApiResponse<InvoicePageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(invoiceService.page(page, size, keyword, customerId, status, dateFrom, dateTo));
    }

    /** 发票详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:invoice:read')")
    public ApiResponse<InvoiceView> get(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.get(id));
    }

    /** 登记手工发票。 */
    @PostMapping
    @PreAuthorize("hasAuthority('finance:invoice:write')")
    public ApiResponse<InvoiceView> create(@Valid @RequestBody InvoiceCreateCommand command) {
        return ApiResponse.ok(invoiceService.create(command));
    }

    /** 标记发票已寄出。 */
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('finance:invoice:write')")
    public ApiResponse<InvoiceView> send(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.markSent(id));
    }

    /** 标记客户已签收。 */
    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAuthority('finance:invoice:write')")
    public ApiResponse<InvoiceView> sign(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.markSigned(id));
    }
}
