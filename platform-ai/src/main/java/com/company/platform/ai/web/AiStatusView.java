package com.company.platform.ai.web;

/**
 * AI 助手运行状态。
 *
 * @param model  配置的模型标签
 * @param note   说明，前端用于展示当前是本地模型还是未配置
 */
public record AiStatusView(String model, String note) {
}
