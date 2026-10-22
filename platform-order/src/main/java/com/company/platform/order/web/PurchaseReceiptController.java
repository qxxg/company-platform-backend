package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.PurchaseReceiptService;
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

/**
 * 采购入库接口。
 *
 * <p>采购入库是库存增加的唯一来源，也是材料成本的唯一来源。单据先建草稿，确认
 * 收货时按物料档案快照单价计算金额（按分截断）并增加库存；已收货单据不可修改，
 * 也不提供作废/红冲（审批中心上线前无通道）。</p>
 *
 * <p>权限：查看需要 {@code warehouse:read}，新建、收货与取消需要
 * {@code warehouse:inbound}。</p>
 */
@RestController
@RequestMapping("/api/order/purchase-receipts")
@Validated
public class PurchaseReceiptController {

    private final PurchaseReceiptService purchaseReceiptService;

    public PurchaseReceiptController(PurchaseReceiptService purchaseReceiptService) {
        this.purchaseReceiptService = purchaseReceiptService;
    }

    /**
     * 采购入库单分页，附带汇总指标。
     *
     * @param page        页码
     * @param size        每页条数
     * @param keyword     单号/供应商关键字
     * @param factoryId   工厂过滤
     * @param warehouseId 仓库过滤
     * @param status      状态过滤
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<PurchaseReceiptPageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(purchaseReceiptService.page(page, size, keyword, factoryId, warehouseId, status));
    }

    /** 入库单详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<PurchaseReceiptView> get(@PathVariable Long id) {
        return ApiResponse.ok(purchaseReceiptService.get(id));
    }

    /** 新建采购入库单（草稿）。 */
    @PostMapping
    @PreAuthorize("hasAuthority('warehouse:inbound')")
    public ApiResponse<PurchaseReceiptView> create(@Valid @RequestBody PurchaseReceiptCreateCommand command) {
        return ApiResponse.ok(purchaseReceiptService.create(command));
    }

    /** 确认收货：按明细增加库存，单据变为已入库。 */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('warehouse:inbound')")
    public ApiResponse<PurchaseReceiptView> receive(@PathVariable Long id) {
        return ApiResponse.ok(purchaseReceiptService.receive(id));
    }

    /** 取消草稿状态的入库单。 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('warehouse:inbound')")
    public ApiResponse<PurchaseReceiptView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(purchaseReceiptService.cancel(id));
    }
}
