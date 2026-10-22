package com.company.platform.order.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 设备上行报文信封。
 *
 * <p>对应《设备接入配置模板与报文示例-v1.0》第 4.1 节的公共报文规范：
 * 版本、类型、设备编号、订单号、毫秒时间戳、设备序号、随机串、签名与业务载荷。
 * {@code rawPayloadJson} 保留设备原始的载荷文本，验签时优先使用，避免服务端
 * 重新序列化导致的签名差异。</p>
 */
public record DeviceEnvelope(
        String version,
        String msgType,
        String deviceNo,
        String orderNo,
        Long timestamp,
        Long seq,
        String nonce,
        String sign,
        JsonNode payload,
        String rawPayloadJson
) {

    /** 报文类型：订单作业数据。 */
    public static final String TYPE_ORDER = "order";
    /** 报文类型：订单图片。 */
    public static final String TYPE_IMAGE = "image";
    /** 报文类型：状态/心跳。 */
    public static final String TYPE_STATUS = "status";
    /** 报文类型：自检。 */
    public static final String TYPE_SELFCHECK = "selfcheck";
    /** 报文类型：异常/故障。 */
    public static final String TYPE_FAULT = "fault";

    /** 设备时间戳转本地时间。 */
    public LocalDateTime reportedAt() {
        return timestamp == null ? LocalDateTime.now()
                : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp),
                        java.time.ZoneId.systemDefault());
    }

    /** 载荷中的字符串字段。 */
    public String payloadText(String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    /** 载荷中的数值字段。 */
    public java.math.BigDecimal payloadNumber(String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }

    /** 载荷中的整数字段。 */
    public Integer payloadInt(String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }

    /** 载荷中的布尔字段。 */
    public Boolean payloadBoolean(String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        return node == null || node.isNull() ? null : node.asBoolean();
    }
}
