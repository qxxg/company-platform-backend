package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.DeviceService;
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

/**
 * 设备台账接口。
 *
 * <p>设备档案由工厂设备管理员维护，厂长只能操作本厂设备；管理员可以跨厂
 * 维护。排产页面需要的设备下拉选项使用单独的只读接口，允许具有排产查看
 * 权限的角色调用。</p>
 *
 * <p>权限：查看需要 {@code equipment:read}，新建/修改/状态变更需要
 * {@code equipment:write}。设备编号和所属工厂不允许通过普通编辑修改，跨厂
 * 移机必须走审批。</p>
 */
@RestController
@RequestMapping("/api/order/devices")
@Validated
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * 设备台账分页。
     *
     * @param page      页码
     * @param size      每页条数，最大 100
     * @param keyword   设备编号/名称/类型/型号关键字
     * @param factoryId 工厂 ID
     * @param status    IDLE/RUNNING/MAINTENANCE/FAULT/DISABLED
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('equipment:read')")
    public ApiResponse<DevicePageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(deviceService.page(page, size, keyword, factoryId, status));
    }

    /**
     * 设备下拉选项。
     *
     * <p>排产人员需要选择同工厂设备，因此拥有 {@code production:schedule:read}
     * 的角色也可以调用；销售可以查看全部工厂的可用设备，方便跟踪订单。</p>
     */
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('equipment:read','production:schedule:read')")
    public ApiResponse<List<DeviceView>> options(@RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(deviceService.options(factoryId));
    }

    /** 设备详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:read')")
    public ApiResponse<DeviceView> get(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.get(id));
    }

    /** 新建设备台账。 */
    @PostMapping
    @PreAuthorize("hasAuthority('equipment:write')")
    public ApiResponse<DeviceView> create(@Valid @RequestBody DeviceCreateCommand command) {
        return ApiResponse.ok(deviceService.create(command));
    }

    /** 修改设备能力和位置；设备编号、所属工厂不可改。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:write')")
    public ApiResponse<DeviceView> update(@PathVariable Long id,
                                          @Valid @RequestBody DeviceUpdateCommand command) {
        return ApiResponse.ok(deviceService.update(id, command));
    }

    /**
     * 人工变更设备状态。
     *
     * <p>只允许空闲、保养、故障、停用；生产中状态由开始工单自动进入。设备正在
     * 生产时不能停机，必须先完成当前工单。</p>
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('equipment:write')")
    public ApiResponse<DeviceView> changeStatus(@PathVariable Long id,
                                                @Valid @RequestBody DeviceStatusCommand command) {
        return ApiResponse.ok(deviceService.changeStatus(id, command));
    }
}
