package com.company.platform.common.core.trace;

import org.slf4j.MDC;

/**
 * 请求追踪号工具。
 *
 * <p>网关生成或透传 {@code X-Request-Id}，Servlet 服务写入 SLF4J MDC，
 * 日志模板通过 {@code traceId} 输出。响应头同时回传该值，前端报错时可以
 * 让运维直接用追踪号定位一次请求。</p>
 */
public final class TraceId {

    /** MDC 中的键名。 */
    public static final String MDC_KEY = "traceId";
    /** HTTP 请求/响应头名称。 */
    public static final String HEADER = "X-Request-Id";

    private TraceId() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }

    public static void set(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
