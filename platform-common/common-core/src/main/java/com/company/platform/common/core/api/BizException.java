package com.company.platform.common.core.api;

/**
 * 业务异常。
 *
 * <p>异常中的 {@code code} 按 HTTP 语义使用：400 参数/业务校验失败、401 未认证、
 * 403 无权限、404 资源不存在、409 状态冲突。全局异常处理器会把它转换为
 * 对应 HTTP 状态和 {@link ApiResponse}，业务代码不需要手工拼响应。</p>
 */
public class BizException extends RuntimeException {

    private final int code;

    /** 默认按 400 处理。 */
    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
