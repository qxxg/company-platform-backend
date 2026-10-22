package com.company.platform.ai.web;

import com.company.platform.ai.service.AiAssistantService;
import com.company.platform.common.core.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 助手接口。
 *
 * <p>所有接口都要求 {@code ai:assistant:use} 权限。对话接口只读，不提供任何
 * 写操作；真正的业务动作仍由各自模块的接口和审批流完成。</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService assistantService;

    public AiController(AiAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /** 健康检查，供启动脚本和网关探活。 */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "platform-ai"));
    }

    /**
     * 对话。
     *
     * @param command 用户问题和会话标识
     */
    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('ai:assistant:use')")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatCommand command) {
        return ApiResponse.ok(assistantService.chat(command));
    }

    /** 当前模型配置，不探测模型是否已加载，避免状态页阻塞。 */
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ai:assistant:use')")
    public ApiResponse<AiStatusView> status() {
        return ApiResponse.ok(new AiStatusView(
                assistantService.modelName(),
                "本地 Ollama 只读助手；业务数据通过当前用户权限实时查询。"));
    }
}
