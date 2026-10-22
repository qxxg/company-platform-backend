package com.company.platform.ai.audit;

/**
 * AI 对话审计条目。
 *
 * @param userId       用户 ID，机器令牌可为空
 * @param username     登录名
 * @param sessionId    会话标识
 * @param question     用户问题
 * @param answer       模型回答
 * @param model        模型标签
 * @param toolTrace    工具调用轨迹 JSON
 * @param durationMs   总耗时
 * @param status       SUCCESS / FAILED
 * @param errorMessage 失败原因
 */
public record AiChatLogEntry(
        Long userId,
        String username,
        String sessionId,
        String question,
        String answer,
        String model,
        String toolTrace,
        long durationMs,
        String status,
        String errorMessage
) {
}
