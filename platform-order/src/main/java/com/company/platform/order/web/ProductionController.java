package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.ProductionBoardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备队列看板接口。
 *
 * <p>按设备分组返回当前生产工单和等待队列，前端用于排产队列页。看板只读，
 * 真正的队列调整由工单和插队审批接口完成。</p>
 *
 * <p>权限：{@code production:schedule:read}。销售可查看全部工厂的设备队列，
 * 用于跟踪自己的订单；厂长只能看本厂。</p>
 */
@RestController
@RequestMapping("/api/order/production")
public class ProductionController {

    private final ProductionBoardService productionBoardService;

    public ProductionController(ProductionBoardService productionBoardService) {
        this.productionBoardService = productionBoardService;
    }

    /**
     * 排产看板。
     *
     * @param factoryId 可选工厂筛选；厂长会被强制限制到本厂
     */
    @GetMapping("/board")
    @PreAuthorize("hasAuthority('production:schedule:read')")
    public ApiResponse<ProductionBoardView> board(@RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(productionBoardService.board(factoryId));
    }
}
