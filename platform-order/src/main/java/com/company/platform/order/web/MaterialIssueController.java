package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.order.service.MaterialIssueService;
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
 * 工单领料接口。
 *
 * <p>领料把仓库物料与生产工单关联起来：建单后为待发料，确认发料时扣减库存并
 * 把金额计入工单材料成本。工单材料成本只对有成本查看权限的角色返回。</p>
 *
 * <p>权限：查看领料单需要 {@code warehouse:read}，建单、发料与取消需要
 * {@code warehouse:outbound}。</p>
 */
@RestController
@RequestMapping("/api/order")
@Validated
public class MaterialIssueController {

    private final MaterialIssueService materialIssueService;

    public MaterialIssueController(MaterialIssueService materialIssueService) {
        this.materialIssueService = materialIssueService;
    }

    /**
     * 领料单分页，附带汇总指标。
     *
     * @param page        页码
     * @param size        每页条数
     * @param keyword     单号/工单号/订单号关键字
     * @param workOrderId 工单过滤
     * @param warehouseId 仓库过滤
     * @param status      状态过滤
     */
    @GetMapping("/material-issues/page")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<MaterialIssuePageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(materialIssueService.page(page, size, keyword, workOrderId, warehouseId, status));
    }

    /** 领料单详情。 */
    @GetMapping("/material-issues/{id}")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<MaterialIssueView> get(@PathVariable Long id) {
        return ApiResponse.ok(materialIssueService.get(id));
    }

    /**
     * 为工单新建领料单（待发料）。
     *
     * <p>建单不扣库存，确认发料时才扣减并计入工单材料成本。</p>
     */
    @PostMapping("/work-orders/{workOrderId}/issues")
    @PreAuthorize("hasAuthority('warehouse:outbound')")
    public ApiResponse<MaterialIssueView> create(@PathVariable Long workOrderId,
                                                @Valid @RequestBody MaterialIssueCreateCommand command) {
        return ApiResponse.ok(materialIssueService.create(workOrderId, command));
    }

    /** 确认发料：扣减库存并计入工单材料成本。 */
    @PostMapping("/material-issues/{id}/issue")
    @PreAuthorize("hasAuthority('warehouse:outbound')")
    public ApiResponse<MaterialIssueView> issue(@PathVariable Long id) {
        return ApiResponse.ok(materialIssueService.issue(id));
    }

    /** 取消待发料的领料单。 */
    @PostMapping("/material-issues/{id}/cancel")
    @PreAuthorize("hasAuthority('warehouse:outbound')")
    public ApiResponse<MaterialIssueView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(materialIssueService.cancel(id));
    }
}
