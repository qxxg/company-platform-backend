package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.InspectionService;
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
 * 设备点检保养记录接口。
 *
 * <p>操作员按日/周/月登记点检结果；结果异常时可以一键生成维护工单，由维护
 * 流程统一安排停机与成本。</p>
 *
 * <p>权限：查看需要 {@code equipment:inspection:read}，登记需要
 * {@code equipment:inspection:write}，由异常点检生成维护工单需要
 * {@code equipment:maintenance:write}。</p>
 */
@RestController
@RequestMapping("/api/order/inspections")
@Validated
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /**
     * 点检记录分页，附带按筛选条件统计的汇总指标。
     *
     * @param page      页码
     * @param size      每页条数，最大 100
     * @param keyword   设备编号/点检内容关键字
     * @param deviceId  设备过滤
     * @param factoryId 工厂过滤
     * @param result    结果过滤：NORMAL/ABNORMAL
     * @param planType  周期过滤：DAILY/WEEKLY/MONTHLY
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('equipment:inspection:read')")
    public ApiResponse<InspectionPageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String planType) {
        return ApiResponse.ok(inspectionService.page(page, size, keyword, deviceId, factoryId, result, planType));
    }

    /** 登记点检记录。 */
    @PostMapping
    @PreAuthorize("hasAuthority('equipment:inspection:write')")
    public ApiResponse<InspectionView> create(@Valid @RequestBody InspectionCreateCommand command) {
        return ApiResponse.ok(inspectionService.create(command));
    }

    /**
     * 由异常点检生成维护工单。
     *
     * <p>生成后的工单为“点检整改”类型、待处理状态，需要排产/维护人员确认后
     * 才开始维护。</p>
     */
    @PostMapping("/{id}/maintenance-order")
    @PreAuthorize("hasAuthority('equipment:maintenance:write')")
    public ApiResponse<MaintenanceView> createMaintenanceOrder(@PathVariable Long id) {
        return ApiResponse.ok(inspectionService.createMaintenanceFromInspection(id));
    }
}
