package com.company.platform.order.web;

/**
 * 设备上行报文回执。
 *
 * <p>回执码与《设备接入配置模板与报文示例-v1.0》第 4.3 节一致：0 成功、
 * 1001 格式错误、1002 不支持的类型、1003 超限、2001 鉴权失败、2002 签名错误、
 * 2003 重放/重复、2004 设备未登记、3001 订单未匹配、3002 订单已关闭、
 * 4001 OCR 失败、4002 图片超限、4003 图片重复、5001 服务端异常。</p>
 */
public record DeviceAck(
        int code,
        String message,
        boolean matched,
        String imageId,
        long seq,
        long serverTime
) {

    /** 成功。 */
    public static DeviceAck ok(long seq, boolean matched, String imageId) {
        return new DeviceAck(0, "ok", matched, imageId, seq, System.currentTimeMillis());
    }

    /** 失败。 */
    public static DeviceAck error(int code, String message, long seq) {
        return new DeviceAck(code, message, false, null, seq, System.currentTimeMillis());
    }
}
