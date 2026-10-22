package com.company.platform.bi.web;

import com.company.platform.bi.etl.BiEtlService;
import com.company.platform.bi.service.BiDashboardService;
import com.company.platform.common.core.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 首页工作台与经营大屏接口。
 *
 * <p>接口只返回聚合结果，不返回客户联系方式、合同正文或成本明细。所有查询都要
 * 经过 {@code bi:dashboard:read} 权限；指标口径弹窗使用 {@code /metrics}，
 * 管理员手动刷新 ETL 使用 {@code /admin/refresh}。响应统一使用平台
 * {@link ApiResponse} 包装，前端按 {@code data} 取值。</p>
 */
@RestController
@RequestMapping("/api/bi")
public class BiDashboardController {

    private final BiDashboardService dashboardService;
    private final BiEtlService etlService;

    public BiDashboardController(BiDashboardService dashboardService, BiEtlService etlService) {
        this.dashboardService = dashboardService;
        this.etlService = etlService;
    }

    /** 获取当前登录人可见的工作台/大屏数据。 */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('bi:dashboard:read')")
    public ApiResponse<BiDashboardView> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }

    /** 指标口径说明；只返回当前用户有权限查看的指标。 */
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('bi:dashboard:read')")
    public ApiResponse<List<BiDashboardView.MetricDefinition>> metrics() {
        return ApiResponse.ok(dashboardService.metrics());
    }

    /** 管理员手动触发一次 ETL；用于演示和排障，不开放给普通角色。 */
    @PostMapping("/admin/refresh")
    @PreAuthorize("hasAuthority('bi:etl:run')")
    public ApiResponse<Map<String, Object>> refresh() {
        boolean executed = etlService.refresh();
        LocalDateTime lastSuccess = etlService.lastSuccessAt();
        return ApiResponse.ok(Map.of(
                "executed", executed,
                "lastStatus", etlService.lastStatus(),
                "dataUpdatedAt", lastSuccess == null ? "" : lastSuccess.toString()));
    }
}
