package com.company.platform.approval.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.approval.service.ApprovalDefinitionService;
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
 * 审批定义（金额档位）维护接口。
 *
 * <p>按 V1.4 规则，审批档位由财务在系统上线前配置：同一业务类型可配置多档，
 * 按金额区间匹配审批人。</p>
 *
 * <p>权限：查看需要 {@code approval:read}，维护需要 {@code approval:manage}。</p>
 */
@RestController
@RequestMapping("/api/approval/definitions")
@Validated
public class ApprovalDefinitionController {

    private final ApprovalDefinitionService definitionService;

    public ApprovalDefinitionController(ApprovalDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    /**
     * 审批定义分页。
     *
     * @param page         页码
     * @param size         每页条数
     * @param businessType 业务类型过滤
     * @param status       状态过滤
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String status) {
        List<ApprovalDefinitionView> records = definitionService.page(page, size, businessType, status);
        long total = definitionService.count(businessType, status);
        return ApiResponse.ok(Map.of("records", records, "total", total, "pageNumber", page, "pageSize", size));
    }

    /** 新建审批定义。 */
    @PostMapping
    @PreAuthorize("hasAuthority('approval:manage')")
    public ApiResponse<ApprovalDefinitionView> create(@Valid @RequestBody ApprovalDefinitionCommand command) {
        return ApiResponse.ok(definitionService.create(command));
    }

    /** 修改审批定义。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('approval:manage')")
    public ApiResponse<ApprovalDefinitionView> update(@PathVariable Long id,
                                                     @Valid @RequestBody ApprovalDefinitionCommand command) {
        return ApiResponse.ok(definitionService.update(id, command));
    }

    /** 启用/停用审批定义。 */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('approval:manage')")
    public ApiResponse<ApprovalDefinitionView> changeStatus(@PathVariable Long id,
                                                            @RequestParam String status) {
        return ApiResponse.ok(definitionService.changeStatus(id, status));
    }
}
