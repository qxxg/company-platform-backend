package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.service.OrderService;
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

import java.util.List;
import java.util.Map;

/**
 * 订单 REST 接口。
 *
 * <p>统一使用 {@link ApiResponse} 包装响应；权限码通过 {@code @PreAuthorize}
 * 在服务端校验，前端隐藏按钮只是体验优化。创建人不是请求参数，而是从 JWT
 * 中读取，防止客户端伪造操作人。</p>
 */
@Validated
@RestController
@RequestMapping("/api/order/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 订单服务健康检查，供启动脚本和网关探活。 */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "platform-order"));
    }

    /**
     * 创建订单。需要 {@code order:create} 权限。
     *
     * @param command 客户、工厂、金额（分）、币种和备注
     */
    @PostMapping
    @PreAuthorize("hasAuthority('order:create')")
    public ApiResponse<OrderView> create(@Valid @RequestBody CreateOrderCommand command) {
        return ApiResponse.ok(orderService.create(command, SecurityUtils.userId()));
    }

    /**
     * 分页查询订单，供 PC Web 订单列表和工作台使用。
     * 所有参数均可选；页码和每页条数设置上限，避免一次拉取过多跨分片数据。
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('order:read')")
    public ApiResponse<PageResult<OrderView>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) Long orderNo,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) String orderStatus) {
        return ApiResponse.ok(orderService.page(page, size, orderNo, customerId, factoryId, orderStatus));
    }

    /**
     * 查询客户订单信用占用，供财务信用看板使用。
     *
     * <p>仅授予财务信用查看权限的角色可调用，避免销售通过该接口读取
     * 其他业务员客户的订单金额汇总。金额单位为分。</p>
     */
    @GetMapping("/occupancy")
    @PreAuthorize("hasAuthority('finance:credit:read')")
    public ApiResponse<CreditOccupancyView> occupancy(@RequestParam Long customerId) {
        return ApiResponse.ok(orderService.creditOccupancy(customerId));
    }

    /** 按订单号查询详情，需要 {@code order:read} 权限。 */
    @GetMapping("/{orderNo}")
    @PreAuthorize("hasAuthority('order:read')")
    public ApiResponse<OrderView> get(@PathVariable long orderNo) {
        return ApiResponse.ok(orderService.get(orderNo));
    }

    /**
     * 兼容接口：按客户查询全部订单。
     *
     * <p>PC Web 列表页使用 {@code /page} 分页接口；此接口保留给后续客户 360
     * 页面或内部脚本使用，不建议在数据量大时直接调用。</p>
     */
    @GetMapping
    @PreAuthorize("hasAuthority('order:read')")
    public ApiResponse<List<OrderView>> listByCustomer(@RequestParam Long customerId) {
        return ApiResponse.ok(orderService.listByCustomer(customerId));
    }
}
