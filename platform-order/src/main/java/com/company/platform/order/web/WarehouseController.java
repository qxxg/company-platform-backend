package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.WarehouseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仓库与物料档案接口。
 *
 * <p>仓库按工厂划分，每个工厂至少一个主仓；物料保存单价（4 位小数）与损耗率
 * （2 位小数），是采购入库与后续领料的共同基础。</p>
 *
 * <p>权限：查看需要 {@code warehouse:read}，新建与修改需要
 * {@code warehouse:manage}。</p>
 */
@RestController
@RequestMapping("/api/order/warehouses")
@Validated
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /**
     * 仓库分页，附带库存概览。
     *
     * @param page      页码
     * @param size      每页条数
     * @param keyword   仓库编号/名称关键字
     * @param factoryId 工厂过滤
     * @param status    状态过滤
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) String status) {
        List<WarehouseView> records = warehouseService.pageWarehouses(page, size, keyword, factoryId, status);
        long total = warehouseService.countWarehouses(factoryId, status);
        return ApiResponse.ok(Map.of("records", records, "total", total, "pageNumber", page, "pageSize", size));
    }

    /** 仓库下拉选项。 */
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('warehouse:read','warehouse:inbound','warehouse:outbound')")
    public ApiResponse<List<WarehouseView>> options(@RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(warehouseService.listWarehouses(factoryId));
    }

    /** 仓库详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<WarehouseView> get(@PathVariable Long id) {
        return ApiResponse.ok(warehouseService.getWarehouse(id));
    }

    /** 新建仓库。 */
    @PostMapping
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<WarehouseView> create(@Valid @RequestBody WarehouseCreateCommand command) {
        return ApiResponse.ok(warehouseService.createWarehouse(command));
    }

    /** 修改仓库；编号和所属工厂不可修改。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<WarehouseView> update(@PathVariable Long id,
                                             @Valid @RequestBody WarehouseUpdateCommand command) {
        return ApiResponse.ok(warehouseService.updateWarehouse(id, command));
    }

    /** 启用/停用仓库；停用前必须清空库存。 */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<WarehouseView> changeStatus(@PathVariable Long id,
                                                   @RequestParam String status) {
        return ApiResponse.ok(warehouseService.changeWarehouseStatus(id, status));
    }
}
