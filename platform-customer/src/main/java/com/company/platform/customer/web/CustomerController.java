package com.company.platform.customer.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.customer.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 客户主数据接口。
 *
 * <p>列表和详情需要 {@code customer:read}，写操作需要 {@code customer:write}。
 * 订单服务通过 {@code order:create} 权限读取下单客户上下文；服务层仍会校验
 * 客户归属，销售不能借订单接口查看他人客户。</p>
 */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** 客户服务健康检查，供启动脚本探活。 */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "platform-customer"));
    }

    /**
     * 分页查询客户。
     *
     * @param page     页码，从 1 开始
     * @param size     每页条数，最大 100
     * @param keyword  名称、编码、手机号或信用代码
     * @param status   客户状态
     * @param ownerUserId 归属业务员；普通销售会被强制为本人
     */
    @GetMapping("/customers/page")
    @PreAuthorize("hasAuthority('customer:read')")
    public ApiResponse<PageResult<CustomerView>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerUserId) {
        return ApiResponse.ok(customerService.page(page, size, keyword, status, ownerUserId));
    }

    /** 按 ID 查询客户详情。 */
    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('customer:read')")
    public ApiResponse<CustomerView> get(@PathVariable Long id) {
        return ApiResponse.ok(customerService.get(id));
    }

    /** 新建客户。销售提交的归属和信用额度会被服务端收紧。 */
    @PostMapping("/customers")
    @PreAuthorize("hasAuthority('customer:write')")
    public ApiResponse<CustomerView> create(@Valid @RequestBody CustomerCreateCommand command) {
        return ApiResponse.ok(customerService.create(command));
    }

    /** 修改客户的非审批字段。 */
    @PutMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('customer:write')")
    public ApiResponse<CustomerView> update(@PathVariable Long id,
                                            @Valid @RequestBody CustomerUpdateCommand command) {
        return ApiResponse.ok(customerService.update(id, command));
    }

    /**
     * 落地审批通过的信用额度调整，仅供审批中心调用。
     *
     * <p>信用额度属于审批字段，页面不能直接改数；审批通过后由审批中心带着审批人
     * 身份调用本接口完成实际落地。</p>
     */
    @PostMapping("/customers/{id}/credit-limit/apply")
    @PreAuthorize("hasAuthority('approval:apply')")
    public ApiResponse<CustomerView> applyCreditLimit(@PathVariable Long id,
                                                      @Valid @RequestBody CreditLimitApplyCommand command) {
        return ApiResponse.ok(customerService.applyCreditLimit(id, command.creditLimit()));
    }

    /**
     * 客户信用辅助信息。应收未收和逾期天数来自客户库；订单占用由订单服务
     * 的分片汇总补充，前端在信用页面合并展示。
     */
    @GetMapping("/customers/{id}/credit")
    @PreAuthorize("hasAuthority('customer:read')")
    public ApiResponse<CustomerCreditView> credit(@PathVariable Long id) {
        return ApiResponse.ok(customerService.credit(id));
    }

    /**
     * 下单客户上下文，仅供订单服务使用。
     *
     * <p>具备 order:create 的角色可以调用，但只能读取自己数据范围内的客户。</p>
     */
    @GetMapping("/customers/{id}/order-context")
    @PreAuthorize("hasAuthority('order:create')")
    public ApiResponse<CustomerOrderContext> orderContext(@PathVariable Long id) {
        return ApiResponse.ok(customerService.orderContext(id));
    }
}
