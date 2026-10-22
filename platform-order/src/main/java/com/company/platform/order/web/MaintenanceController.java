package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.MaintenanceService;
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

/**
 * 设备维护工单接口。
 *
 * <p>维护工单由工厂设备管理员或厂长发起：新建后设备状态不变，开始维护时
 * 设备转为保养或故障状态，等待队列保留并通知受影响业务员，完成后设备恢复
 * 空闲并重算队列计划时间。</p>
 *
 * <p>维护费用全部由公司承担，系统只做成本记录，不生成应收也不计算设备使用
 * 费。</p>
 *
 * <p>权限：查看需要 {@code equipment:maintenance:read}，新建、开始、完成和取消
 * 需要 {@code equipment:maintenance:write}。</p>
 */
@RestController
@RequestMapping("/api/order/maintenance-orders")
@Validated
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /**
     * 维护工单分页，附带按筛选条件统计的汇总指标。
     *
     * @param page      页码
     * @param size      每页条数，最大 100
     * @param keyword   维护单号/设备编号/主题关键字
     * @param deviceId  设备过滤
     * @param factoryId 工厂过滤
     * @param status    状态过滤
     * @param type      维护类型过滤
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('equipment:maintenance:read')")
    public ApiResponse<MaintenancePageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(maintenanceService.page(page, size, keyword, deviceId, factoryId, status, type));
    }

    /** 维护工单详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:maintenance:read')")
    public ApiResponse<MaintenanceView> get(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceService.get(id));
    }

    /** 某台设备的维护工单，设备台账页按设备查看历史时使用。 */
    @GetMapping("/by-device/{deviceId}")
    @PreAuthorize("hasAuthority('equipment:maintenance:read')")
    public ApiResponse<List<MaintenanceView>> byDevice(@PathVariable Long deviceId) {
        return ApiResponse.ok(maintenanceService.listByDevice(deviceId));
    }

    /** 新建维护工单；设备状态保持不变，排产不受影响。 */
    @PostMapping
    @PreAuthorize("hasAuthority('equipment:maintenance:write')")
    public ApiResponse<MaintenanceView> create(@Valid @RequestBody MaintenanceCreateCommand command) {
        return ApiResponse.ok(maintenanceService.create(command));
    }

    /**
     * 开始维护：设备转为保养或故障状态。
     *
     * <p>生产中的设备不允许停机，必须先完成当前工单。</p>
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('equipment:maintenance:write')")
    public ApiResponse<MaintenanceView> start(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceService.start(id));
    }

    /** 完成维护：登记成本与停机时长，设备恢复空闲并重算队列计划时间。 */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('equipment:maintenance:write')")
    public ApiResponse<MaintenanceView> complete(@PathVariable Long id,
                                                 @Valid @RequestBody MaintenanceCompleteCommand command) {
        return ApiResponse.ok(maintenanceService.complete(id, command));
    }

    /** 取消待处理的维护工单。 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('equipment:maintenance:write')")
    public ApiResponse<MaintenanceView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceService.cancel(id));
    }
}
