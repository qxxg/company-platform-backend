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
 * 物料档案接口。
 *
 * <p>物料是采购入库、库存和后续工单领料的共同基础；单价最多 4 位小数、损耗率
 * 2 位小数，物料编码全公司唯一且创建后不可修改。</p>
 *
 * <p>权限：查看需要 {@code warehouse:read}，新建与修改需要
 * {@code warehouse:manage}。</p>
 */
@RestController
@RequestMapping("/api/order/materials")
@Validated
public class MaterialController {

    private final WarehouseService warehouseService;

    public MaterialController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /**
     * 物料分页，附带库存合计。
     *
     * @param page         页码
     * @param size         每页条数
     * @param keyword      物料编码/名称/规格关键字
     * @param materialType 物料类型过滤
     * @param status       状态过滤
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) String status) {
        List<MaterialView> records = warehouseService.pageMaterials(page, size, keyword, materialType, status);
        long total = warehouseService.countMaterials(keyword, materialType, status);
        return ApiResponse.ok(Map.of("records", records, "total", total, "pageNumber", page, "pageSize", size));
    }

    /** 物料下拉选项：只返回启用物料。 */
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('warehouse:read','warehouse:inbound','warehouse:outbound')")
    public ApiResponse<List<MaterialView>> options() {
        return ApiResponse.ok(warehouseService.listMaterials());
    }

    /** 物料详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<MaterialView> get(@PathVariable Long id) {
        return ApiResponse.ok(warehouseService.getMaterial(id));
    }

    /** 新建物料档案。 */
    @PostMapping
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<MaterialView> create(@Valid @RequestBody MaterialCreateCommand command) {
        return ApiResponse.ok(warehouseService.createMaterial(command));
    }

    /** 修改物料档案；物料编码不可修改。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<MaterialView> update(@PathVariable Long id,
                                            @Valid @RequestBody MaterialUpdateCommand command) {
        return ApiResponse.ok(warehouseService.updateMaterial(id, command));
    }

    /** 启用/停用物料；仍有库存的物料不允许停用。 */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<MaterialView> changeStatus(@PathVariable Long id,
                                                  @RequestParam String status) {
        return ApiResponse.ok(warehouseService.changeMaterialStatus(id, status));
    }
}
