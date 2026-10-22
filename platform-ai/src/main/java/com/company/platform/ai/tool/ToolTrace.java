package com.company.platform.ai.tool;

/**
 * 一次工具调用的审计轨迹。
 *
 * @param name        工具名
 * @param label       中文标签，前端用于展示“已查询”
 * @param success     是否成功返回可用数据
 * @param durationMs  耗时
 * @param summary     结果摘要，仅用于审计，前端可忽略
 */
public record ToolTrace(
        String name,
        String label,
        boolean success,
        long durationMs,
        String summary
) {
}
