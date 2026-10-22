package com.company.platform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 助手配置。
 *
 * <p>模型参数故意做成外部配置：本地服务器资源紧张时，可以只调小上下文和
 * 最大输出，不必重新编译。工具调用仍然只读，所以这里的超时和轮数只影响
 * 响应速度，不影响业务数据安全。</p>
 *
 * @param ollama         Ollama 连接与采样参数
 * @param services       内部业务服务地址
 * @param maxIterations  ReAct 最大轮数
 * @param timeoutSeconds 单次对话总超时
 * @param stateDir       AgentScope 会话状态目录
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        Ollama ollama,
        Services services,
        int maxIterations,
        int timeoutSeconds,
        String stateDir
) {

    /**
     * Ollama 参数。
     *
     * @param baseUrl       Ollama HTTP 地址，例如 http://192.168.3.44:11434
     * @param model         模型标签，必须与 `ollama list` 中的名称一致
     * @param contextLength 上下文长度；CPU 推理时不要盲目放大
     * @param maxTokens     单轮最大输出 token
     * @param temperature   采样温度，业务摘要建议 0.1~0.3
     */
    public record Ollama(
            String baseUrl,
            String model,
            int contextLength,
            int maxTokens,
            double temperature
    ) {
    }

    /**
     * 内部服务地址。走 Nacos 服务名和 Spring Cloud LoadBalancer，避免硬编码端口。
     *
     * @param orderUrl         订单服务
     * @param customerUrl      客户与财务单据服务
     * @param approvalUrl      审批中心
     * @param connectTimeoutMs 连接超时
     * @param readTimeoutMs    读取超时
     */
    public record Services(
            String orderUrl,
            String customerUrl,
            String approvalUrl,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
    }
}
