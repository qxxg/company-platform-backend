package com.company.platform.customer.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.customer.service.ReceiptService;
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
 * 财务收款登记与核销接口。
 *
 * <p>收款单登记和核销分离：先登记实际到账，再选择客户未结应收分配。
 * 核销接口在事务内加锁并重新校验余额，前端展示的余额不能作为最终依据。</p>
 *
 * <p>权限：查看需要 {@code finance:receipt:read}，登记/核销需要
 * {@code finance:receipt:write}。当前只授予财务和管理员，销售只能查看
 * 应收，不开放收款登记。</p>
 */
@RestController
@RequestMapping("/api/finance/receipts")
public class FinanceReceiptController {

    private final ReceiptService receiptService;

    public FinanceReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    /**
     * 收款单分页。
     *
     * @param page       页码
     * @param size       每页条数，最大 100
     * @param keyword    客户名称/编码、流水号或收款单号
     * @param customerId 客户 ID
     * @param status     UNALLOCATED/PARTIAL/ALLOCATED/CANCELLED
     * @param dateFrom   收款日起
     * @param dateTo     收款日止
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:receipt:read')")
    public ApiResponse<PageResult<ReceiptView>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(receiptService.page(page, size, keyword, customerId, status, dateFrom, dateTo));
    }

    /** 收款详情，包含核销明细。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:receipt:read')")
    public ApiResponse<ReceiptView> get(@PathVariable Long id) {
        return ApiResponse.ok(receiptService.get(id));
    }

    /** 登记收款；只记录到账，不直接修改应收。 */
    @PostMapping
    @PreAuthorize("hasAuthority('finance:receipt:write')")
    public ApiResponse<ReceiptView> create(@Valid @RequestBody ReceiptCreateCommand command) {
        return ApiResponse.ok(receiptService.create(command));
    }

    /** 核销收款到一张或多张应收。 */
    @PostMapping("/{id}/allocations")
    @PreAuthorize("hasAuthority('finance:receipt:write')")
    public ApiResponse<ReceiptView> allocate(@PathVariable Long id,
                                             @Valid @RequestBody ReceiptAllocateCommand command) {
        return ApiResponse.ok(receiptService.allocate(id, command));
    }
}
