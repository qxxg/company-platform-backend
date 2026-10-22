package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.StockTransferService;
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
 * 跨厂调拨接口。
 *
 * <p>调拨是库存“转移”的唯一通道：出库扣减调出仓库、入库增加调入仓库，数量不
 * 允许为负。跨厂调拨允许，先出库后入库，由两边工厂分别确认。</p>
 *
 * <p>权限：查看需要 {@code warehouse:read}，新建与出库需要
 * {@code warehouse:outbound}，入库需要 {@code warehouse:inbound}。</p>
 */
@RestController
@RequestMapping("/api/order/stock-transfers")
@Validated
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(StockTransferService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    /**
     * 调拨单分页，附带汇总指标。
     *
     * @param page      页码
     * @param size      每页条数
     * @param keyword   单号关键字
     * @param status    状态过滤
     * @param factoryId 工厂过滤：只看与该工厂相关的调拨
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<StockTransferPageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(stockTransferService.page(page, size, keyword, status, factoryId));
    }

    /** 调拨单详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<StockTransferView> get(@PathVariable Long id) {
        return ApiResponse.ok(stockTransferService.get(id));
    }

    /** 新建调拨单（待出库）。 */
    @PostMapping
    @PreAuthorize("hasAuthority('warehouse:outbound')")
    public ApiResponse<StockTransferView> create(@Valid @RequestBody StockTransferCreateCommand command) {
        return ApiResponse.ok(stockTransferService.create(command));
    }

    /** 出库：扣减调出仓库库存，单据变为在途。 */
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('warehouse:outbound')")
    public ApiResponse<StockTransferView> ship(@PathVariable Long id) {
        return ApiResponse.ok(stockTransferService.ship(id));
    }

    /** 入库：增加调入仓库库存，单据变为已入库。 */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('warehouse:inbound')")
    public ApiResponse<StockTransferView> receive(@PathVariable Long id) {
        return ApiResponse.ok(stockTransferService.receive(id));
    }

    /** 取消待出库的调拨单。 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('warehouse:outbound')")
    public ApiResponse<StockTransferView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(stockTransferService.cancel(id));
    }
}
