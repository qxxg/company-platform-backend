package com.company.platform.order.client;

import com.company.platform.common.core.api.BizException;
import com.company.platform.order.config.DeviceReportingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Apache IoTDB REST 客户端。
 *
 * <p>自检、异常/故障与心跳属于时序数据，按 V1.4 规则落 IoTDB 而不是 MySQL。
 * 这里使用 IoTDB 2.x 的 OpenAPI v2：写入走 {@code POST /rest/v2/nonQuery}，
 * 查询走 {@code POST /rest/v2/query}，均使用 Basic 鉴权。</p>
 *
 * <p>数据库下按设备编号建 schema，测点列按消息类型固定：温度、墨量百分比、故障码等。
 * 写入失败不影响主流程：调用方记录日志并继续，MySQL 侧的接入审计仍会落库。</p>
 */
@Component
public class IoTdbRestClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DeviceReportingProperties properties;

    public IoTdbRestClient(ObjectMapper objectMapper,
                           DeviceReportingProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        // IoTDB 使用绝对地址访问，不能复用带 @LoadBalanced 的业务 RestTemplate，
        // 否则会把它当成服务名解析；这里单独构建带超时的普通 RestTemplate。
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.iotdb.timeoutSeconds));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 写入一条时序数据。
     *
     * @param deviceNo 设备编号
     * @param measurements 测点名到数值的映射（值可以是数值或文本）
     * @param timestamp 毫秒时间戳
     */
    public void write(String deviceNo, Map<String, Object> measurements, long timestamp) {
        if (!properties.iotdb.enabled) {
            return;
        }
        if (measurements.isEmpty()) {
            return;
        }
        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();
        columns.add("time");
        values.add(String.valueOf(timestamp));
        for (Map.Entry<String, Object> entry : measurements.entrySet()) {
            columns.add(sanitize(entry.getKey()));
            values.add(toSqlValue(entry.getValue()));
        }
        String sql = "INSERT INTO " + database() + "." + schemaOf(deviceNo)
                + "(" + String.join(",", columns) + ") VALUES(" + String.join(",", values) + ")";
        try {
            String body = objectMapper.writeValueAsString(Map.of("sql", sql));
            restTemplate.postForEntity(baseUrl() + "/rest/v2/nonQuery", new HttpEntity<>(body, jsonHeaders()),
                    String.class);
        } catch (RestClientResponseException ex) {
            throw new BizException(502, "IoTDB 写入失败：" + ex.getMessage());
        } catch (Exception ex) {
            throw new BizException(502, "IoTDB 写入异常：" + ex.getMessage());
        }
    }

    /**
     * 查询设备的时序数据。
     *
     * @param deviceNo 设备编号
     * @param measurement 测点名
     * @param limit 返回条数上限
     * @return 时间戳与数值列表
     */
    public List<Map<String, Object>> query(String deviceNo, String measurement, int limit) {
        if (!properties.iotdb.enabled) {
            return List.of();
        }
        String sql = "select " + sanitize(measurement) + " from " + database() + "." + schemaOf(deviceNo)
                + " limit " + Math.max(1, limit);
        try {
            String body = objectMapper.writeValueAsString(Map.of("sql", sql));
            String response = restTemplate.postForEntity(baseUrl() + "/rest/v2/query",
                    new HttpEntity<>(body, jsonHeaders()), String.class).getBody();
            if (response == null) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode timestamps = root.path("timestamps");
            JsonNode values = root.path("values");
            List<Map<String, Object>> result = new ArrayList<>();
            if (!timestamps.isArray() || values.size() == 0) {
                return result;
            }
            for (int i = 0; i < timestamps.size(); i++) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("timestamp", timestamps.get(i).asLong());
                JsonNode columnValues = values.get(0);
                row.put("value", columnValues != null && i < columnValues.size()
                        ? columnValues.get(i).asText() : null);
                result.add(row);
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw new BizException(502, "IoTDB 查询失败：" + ex.getMessage());
        } catch (Exception ex) {
            throw new BizException(502, "IoTDB 查询异常：" + ex.getMessage());
        }
    }

    /** 数据库名。 */
    public String database() {
        return properties.iotdb.database;
    }

    /** 设备对应的 schema 名：把设备编号里的非法字符替换成下划线。 */
    public String schemaOf(String deviceNo) {
        String prefix = properties.iotdb.schemaPrefix;
        String suffix = deviceNo == null ? "unknown" : deviceNo.replaceAll("[^A-Za-z0-9_]", "_");
        return prefix + "_" + suffix;
    }

    /** REST 服务地址。 */
    private String baseUrl() {
        return "http://" + properties.iotdb.host + ":" + properties.iotdb.port;
    }

    /** Basic 鉴权 + JSON 请求头。 */
    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String raw = properties.iotdb.username + ":" + properties.iotdb.password;
        headers.set("Authorization", "Basic "
                + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        return headers;
    }

    /** 测点名清洗：只保留字母数字下划线。 */
    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /** 数值转 SQL 字面量；文本转带引号字符串。 */
    private static String toSqlValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value instanceof Boolean flag) {
            return flag ? "true" : "false";
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }
}
