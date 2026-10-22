package com.company.platform.customer.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.customer.service.ContractService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户合同接口。
 *
 * <p>合同列表和录入需要财务权限；订单创建时通过受控的 available 和
 * order-context 接口读取合同，不开放完整合同内容。所有金额单位为分。</p>
 */
@RestController
@RequestMapping("/api/customer/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    /**
     * 财务合同分页列表。
     *
     * @param page       页码
     * @param size       每页条数，最大 100
     * @param keyword    合同编号模糊过滤
     * @param customerId 客户 ID
     * @param status     合同状态
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:contract:read')")
    public ApiResponse<PageResult<ContractView>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(contractService.page(page, size, keyword, customerId, status));
    }

    /** 财务新建合同，初始状态为“待财务录入”。 */
    @PostMapping
    @PreAuthorize("hasAuthority('finance:contract:write')")
    public ApiResponse<ContractView> create(@Valid @RequestBody ContractCreateCommand command) {
        return ApiResponse.ok(contractService.create(command));
    }

    /** 财务正式录入合同；录入后订单才可引用。 */
    @PostMapping("/{id}/enter")
    @PreAuthorize("hasAuthority('finance:contract:write')")
    public ApiResponse<ContractView> enter(@PathVariable Long id) {
        return ApiResponse.ok(contractService.enter(id));
    }

    /** 查看合同详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:contract:read')")
    public ApiResponse<ContractView> get(@PathVariable Long id) {
        return ApiResponse.ok(contractService.get(id));
    }

    /**
     * 某客户当前可下单的合同。
     *
     * <p>销售在订单页选择合同时调用；只返回已录入/生效且未到期的合同，并按
     * 客户归属做数据隔离。</p>
     */
    @GetMapping("/available")
    @PreAuthorize("hasAuthority('order:create')")
    public ApiResponse<List<ContractView>> available(@RequestParam Long customerId) {
        return ApiResponse.ok(contractService.available(customerId));
    }

    /**
     * 下单合同上下文，仅供订单服务校验合同状态、客户归属和金额使用。
     */
    @GetMapping("/{id}/order-context")
    @PreAuthorize("hasAuthority('order:create')")
    public ApiResponse<ContractOrderContext> orderContext(@PathVariable Long id) {
        return ApiResponse.ok(contractService.orderContext(id));
    }
}
