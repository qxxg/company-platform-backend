package com.company.platform.customer.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.customer.service.ReceivableService;
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
import java.util.List;

/**
 * 财务应收账款接口。
 *
 * <p>对外路径统一为 {@code /api/finance/**}，当前由客户服务托管，因为它与
 * 客户信用、合同共用 {@code company_platform} 数据库；后续拆分独立财务服务时
 * 只需迁移控制器和表，网关路由改为新服务即可，前端路径不变。</p>
 *
 * <p>权限：查看需要 {@code finance:receivable:read}，手工补录需要
 * {@code finance:receivable:write}。财务和管理员拥有全部权限；销售如被授予
 * 查看权限，只能看到归属自己的客户应收。</p>
 */
@RestController
@RequestMapping("/api/finance/receivables")
public class FinanceReceivableController {

    private final ReceivableService receivableService;

    public FinanceReceivableController(ReceivableService receivableService) {
        this.receivableService = receivableService;
    }

    /**
     * 应收分页。
     *
     * @param page       页码
     * @param size       每页条数，最大 100
     * @param keyword    客户名称/编码/应收单号
     * @param customerId 客户 ID
     * @param status     UNPAID/PARTIAL/PAID/CANCELLED/OPEN/OVERDUE
     * @param dueFrom    到期日起
     * @param dueTo      到期日止
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:receivable:read')")
    public ApiResponse<ReceivablePageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo) {
        return ApiResponse.ok(receivableService.page(page, size, keyword, customerId, status, dueFrom, dueTo));
    }

    /** 某客户未结清应收，供收款核销弹窗选择。 */
    @GetMapping("/open")
    @PreAuthorize("hasAuthority('finance:receivable:read')")
    public ApiResponse<List<ReceivableView>> open(@RequestParam Long customerId) {
        return ApiResponse.ok(receivableService.openByCustomer(customerId));
    }

    /** 应收详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:receivable:read')")
    public ApiResponse<ReceivableView> get(@PathVariable Long id) {
        return ApiResponse.ok(receivableService.get(id));
    }

    /**
     * 财务手工补录应收。
     *
     * <p>正式应收由发货/开票自动生成；该接口用于当前阶段补录和调整，来源
     * 固定标记为 MANUAL，不提供物理删除。</p>
     */
    @PostMapping
    @PreAuthorize("hasAuthority('finance:receivable:write')")
    public ApiResponse<ReceivableView> create(@Valid @RequestBody ReceivableCreateCommand command) {
        return ApiResponse.ok(receivableService.create(command));
    }
}
