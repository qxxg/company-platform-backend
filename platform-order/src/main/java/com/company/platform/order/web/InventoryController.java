package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.WarehouseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 库存台账接口。
 *
 * <p>库存只读：增加只能通过采购入库和调拨入库，减少只能通过调拨出库（以及
 * 后续的工单领料），系统不提供库存调整入口，因此没有期初库存。</p>
 *
 * <p>权限：需要 {@code warehouse:read}。</p>
 */
@RestController
@RequestMapping("/api/order/inventories")
@Validated
public class InventoryController {

    private final WarehouseService warehouseService;

    public InventoryController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /**
     * 库存分页。
     *
     * @param page        页码
     * @param size        每页条数
     * @param warehouseId 仓库过滤
     * @param factoryId   工厂过滤
     * @param materialId  物料过滤
     * @param keyword     物料编码/名称关键字
     * @param belowSafety 只看低于安全库存
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean belowSafety) {
        List<InventoryView> records = warehouseService.pageInventory(page, size, warehouseId, factoryId,
                materialId, keyword, belowSafety);
        long total = warehouseService.countInventory(warehouseId, factoryId, materialId);
        return ApiResponse.ok(Map.of("records", records, "total", total, "pageNumber", page, "pageSize", size));
    }
}
