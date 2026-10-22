package com.company.platform.common.core.api;

import com.company.platform.common.core.trace.TraceId;

import java.time.Instant;

/**
 * 统一 API 响应包装。
 *
 * <p>{@code code = 0} 表示成功，非 0 表示业务失败；HTTP 状态码同时保留，
 * 前端既可以用 HTTP 状态判断 401/403，也可以读取业务码展示具体原因。
 * {@code traceId} 来自当前请求的 MDC，方便把前端提示与后端日志对应起来。</p>
 */
public record ApiResponse<T>(int code, String message, T data, String traceId, Instant timestamp) {

    /** 成功响应。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "OK", data, TraceId.current(), Instant.now());
    }

    /** 无数据的成功响应。 */
    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    /** 业务失败响应，不携带数据。 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceId.current(), Instant.now());
    }

    /** 业务失败响应，可携带字段级错误等附加数据。 */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, TraceId.current(), Instant.now());
    }
}
