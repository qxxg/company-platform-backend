package com.company.platform.ai.web;

import com.company.platform.ai.tool.ToolTrace;

import java.util.List;

/**
 * AI 助手对话响应。
 *
 * @param sessionId  本轮会话标识，前端下一轮原样带回
 * @param answer     模型回答
 * @param model      实际使用的模型标签
 * @param toolCalls  本轮调用的只读工具
 * @param elapsedMs  服务端总耗时
 */
public record ChatResponse(
        String sessionId,
        String answer,
        String model,
        List<ToolTrace> toolCalls,
        long elapsedMs
) {
}
