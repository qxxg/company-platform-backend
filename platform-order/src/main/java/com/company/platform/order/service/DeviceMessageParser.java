package com.company.platform.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 设备上行报文解析器。
 *
 * <p>把接入层拿到的 JSON 转成 {@link DeviceEnvelope}，并保留载荷原文用于验签。
 * 三种接入方式（MQTT/CoAP/HTTP）共用这一份解析逻辑。</p>
 *
 * <p>注意：报文原文中的 {@code payload} 子串必须原样保留，不能用 Jackson 重新
 * 序列化后再验签。设备侧按自己的紧凑 JSON 计算签名，重新序列化可能改变数字
 * 格式（如 12.500 变 12.5），导致签名不一致。</p>
 */
public final class DeviceMessageParser {

    private DeviceMessageParser() {
    }

    /**
     * 解析报文。
     *
     * @param root           报文根节点
     * @param rawPayloadJson 载荷原文（验签用，可为空）
     * @return 报文信封
     */
    public static DeviceEnvelope parse(JsonNode root, String rawPayloadJson) {
        JsonNode payload = root == null ? null : root.get("payload");
        String raw = rawPayloadJson;
        if (raw == null && payload != null) {
            raw = payload.toString();
        }
        return new DeviceEnvelope(text(root, "version"), text(root, "msgType"),
                text(root, "deviceNo"), text(root, "orderNo"),
                number(root, "timestamp"), number(root, "seq"), text(root, "nonce"),
                text(root, "sign"), payload, raw);
    }

    /**
     * 解析报文文本。载荷原文从报文里按花括号配对截取，保证与设备签名一致。
     *
     * @param json 报文 JSON 文本
     * @return 报文信封
     * @throws Exception JSON 非法时抛出
     */
    public static DeviceEnvelope parse(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        return parse(root, extractRawPayload(json));
    }

    /**
     * 从报文原文中截取 payload 子串（含花括号）。
     *
     * <p>按字符串与转义规则扫描，避免把 payload 内部的字符串内容误判为结构。</p>
     *
     * @param json 报文原文
     * @return payload 子串；找不到返回 null
     */
    public static String extractRawPayload(String json) {
        if (json == null) {
            return null;
        }
        int keyIndex = json.indexOf("\"payload\"");
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + 9);
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        char open = json.charAt(start);
        char close = open == '{' ? '}' : open == '[' ? ']' : 0;
        if (close == 0) {
            // 标量载荷（字符串/数字）按逗号或右花括号截断。
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(start, end).trim();
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char current = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /** 读取文本字段。 */
    private static String text(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    /** 读取整数字段。 */
    private static Long number(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asLong();
    }
}
