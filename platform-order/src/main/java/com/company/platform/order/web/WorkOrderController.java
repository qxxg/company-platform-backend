package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.service.WorkOrderService;
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
 * 生产工单与设备队列操作接口。
 *
 * <p>工单由排产人员创建，可选择先进入“待排产”，再分配同工厂设备。工单进入
 * 设备队列后按 FIFO 等待；开始/完成由排产人员或设备管理员操作。销售只读
 * 自己订单的工单进度，不能直接创建或开始工单。</p>
 *
 * <p>权限：查看需要 {@code production:schedule:read}，创建、分配、开始、完成、
 * 取消需要 {@code production:schedule:write}。</p>
 */
@RestController
@RequestMapping("/api/order/work-orders")
@Validated
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    /**
     * 工单分页。
     *
     * @param page      页码
     * @param size      每页条数，最大 100
     * @param orderNo   订单号
     * @param factoryId 工厂 ID
     * @param deviceId  设备 ID
     * @param status    PENDING/QUEUED/RUNNING/COMPLETED/CANCELLED
     * @param keyword   工单号/材料/工艺关键字
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('production:schedule:read')")
    public ApiResponse<PageResult<WorkOrderView>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) Long orderNo,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(workOrderService.page(page, size, orderNo, factoryId, deviceId, status, keyword));
    }

    /** 工单详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('production:schedule:read')")
    public ApiResponse<WorkOrderView> get(@PathVariable Long id) {
        return ApiResponse.ok(workOrderService.get(id));
    }

    /**
     * 查询某订单的全部工单。
     *
     * <p>订单详情页使用；销售只能查看自己订单的工单，厂长按工厂范围查看。</p>
     */
    @GetMapping("/by-order/{orderNo}")
    @PreAuthorize("hasAuthority('production:schedule:read')")
    public ApiResponse<List<WorkOrderView>> byOrder(@PathVariable Long orderNo) {
        return ApiResponse.ok(workOrderService.byOrder(orderNo));
    }

    /** 创建工单；deviceId 可为空，表示先进入待排产。 */
    @PostMapping
    @PreAuthorize("hasAuthority('production:schedule:write')")
    public ApiResponse<WorkOrderView> create(@Valid @RequestBody WorkOrderCreateCommand command) {
        return ApiResponse.ok(workOrderService.create(command, SecurityUtils.userId()));
    }

    /** 给待排产工单分配同工厂设备。 */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('production:schedule:write')")
    public ApiResponse<WorkOrderView> assign(@PathVariable Long id,
                                             @Valid @RequestBody WorkOrderAssignCommand command) {
        return ApiResponse.ok(workOrderService.assign(id, command));
    }

    /** 开始队首工单；设备同一时间只能有一个生产中工单。 */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('production:schedule:write')")
    public ApiResponse<WorkOrderView> start(@PathVariable Long id) {
        return ApiResponse.ok(workOrderService.start(id));
    }

    /** 完成生产中工单，释放设备并顺延队列。 */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('production:schedule:write')")
    public ApiResponse<WorkOrderView> complete(@PathVariable Long id) {
        return ApiResponse.ok(workOrderService.complete(id));
    }

    /** 取消待排产或排队中工单；生产中工单不能直接取消。 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('production:schedule:write')")
    public ApiResponse<WorkOrderView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(workOrderService.cancel(id));
    }
}
