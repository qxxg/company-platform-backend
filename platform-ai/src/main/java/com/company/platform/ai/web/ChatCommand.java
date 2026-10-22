package com.company.platform.ai.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AI 助手对话请求。
 *
 * @param message   用户问题，限制长度避免把超长文本塞进本地小模型上下文
 * @param sessionId 浏览器生成的会话标识；为空时由服务端生成
 */
public record ChatCommand(
        @NotBlank(message = "请输入问题")
        @Size(max = 2000, message = "问题不能超过 2000 个字符")
        String message,

        @Pattern(regexp = "^$|^[A-Za-z0-9_-]{8,64}$", message = "会话标识不合法")
        String sessionId
) {
}
