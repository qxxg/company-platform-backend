package com.company.platform.ai.config;

import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.extensions.model.ollama.OllamaChatModel;
import io.agentscope.extensions.model.ollama.options.OllamaOptions;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.time.Duration;

/**
 * AgentScope 与内部服务客户端配置。
 *
 * <p>模型只创建一次并复用；每次对话按用户和会话构建独立的 ReActAgent。
 * 这样既共享昂贵的 HTTP 连接池，又不会把不同用户的会话上下文混在一起。</p>
 */
@Configuration
public class AiAgentConfig {

    /**
     * 本地 Ollama 模型。
     *
     * <p>关闭流式输出是为了让第一版控制器可以用阻塞调用返回完整答案；
     * 后续接入 SSE 时只需打开 stream 并改造控制器。这里把超时和重试
     * 固定为“最多一次”，避免 4B 模型在 CPU 上重复执行两遍长推理。</p>
     */
    @Bean
    public OllamaChatModel ollamaChatModel(AiProperties properties) {
        AiProperties.Ollama ollama = properties.ollama();
        OllamaOptions options = OllamaOptions.builder()
                .numCtx(ollama.contextLength())
                .numPredict(ollama.maxTokens())
                .temperature(ollama.temperature())
                .executionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                        .maxAttempts(1)
                        .build())
                .build();
        return OllamaChatModel.builder()
                .modelName(ollama.model())
                .baseUrl(ollama.baseUrl())
                .stream(false)
                .defaultOptions(options)
                .build();
    }

    /**
     * 会话状态存储。按 userId/sessionId 分目录，服务重启后仍可继续多轮对话。
     *
     * <p>状态目录只保存对话上下文，不保存业务权限；每次请求仍重新读取 JWT，
     * 所以用户被撤权后下一次请求会立即被业务接口拒绝。</p>
     */
    @Bean
    public AgentStateStore agentStateStore(AiProperties properties) {
        return new JsonFileAgentStateStore(Path.of(properties.stateDir()));
    }

    /**
     * 带 Nacos 负载均衡的只读业务查询客户端。
     *
     * <p>AI 工具调用时透传当前用户 JWT，由目标服务执行真正的权限和数据范围
     * 校验。AI 服务本身没有绕过业务接口的数据库访问通道。</p>
     */
    @Bean
    @LoadBalanced
    public RestTemplate aiBusinessRestTemplate(AiProperties properties) {
        AiProperties.Services services = properties.services();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(services.connectTimeoutMs());
        factory.setReadTimeout(services.readTimeoutMs());
        return new RestTemplate(factory);
    }
}
