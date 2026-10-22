package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.client.IoTdbRestClient;
import com.company.platform.order.config.DeviceReportingProperties;
import com.company.platform.order.entity.BizDeviceAccessLog;
import com.company.platform.order.entity.BizDeviceCredential;
import com.company.platform.order.entity.BizDeviceImage;
import com.company.platform.order.entity.BizDeviceOrderReport;
import com.company.platform.order.entity.BizDeviceReportStaging;
import com.company.platform.order.entity.BizOrder;
import com.company.platform.order.mapper.BizDeviceAccessLogMapper;
import com.company.platform.order.mapper.BizDeviceCredentialMapper;
import com.company.platform.order.mapper.BizDeviceImageMapper;
import com.company.platform.order.mapper.BizDeviceOrderReportMapper;
import com.company.platform.order.mapper.BizDeviceReportStagingMapper;
import com.company.platform.order.mapper.BizOrderMapper;
import com.company.platform.order.web.DeviceAck;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备数据上报处理服务。
 *
 * <p>统一处理 MQTT / CoAP / HTTP 三种接入方式的上行报文，流程与《设备接入配置
 * 模板与报文示例-v1.0》一致：</p>
 * <ol>
 *   <li>校验报文结构与设备登记状态（未登记返回 2004）；</li>
 *   <li>按设备一机一密密钥验签（签名错误返回 2002）；</li>
 *   <li>校验时间戳偏差（超出重放窗口返回 2003）与 deviceNo+seq 去重；</li>
 *   <li>按报文类型落地：订单作业数据写 MySQL（按订单号覆盖），自检/异常/心跳写
 *       IoTDB，订单图片按订单唯一；</li>
 *   <li>订单号无法精确匹配时暂存并返回 3001，匹配成功后归位。</li>
 * </ol>
 */
@Service
public class DeviceReportingService {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceReportingService.class);

    private final BizDeviceCredentialMapper credentialMapper;
    private final BizDeviceOrderReportMapper orderReportMapper;
    private final BizDeviceImageMapper imageMapper;
    private final BizDeviceReportStagingMapper stagingMapper;
    private final BizDeviceAccessLogMapper accessLogMapper;
    private final BizOrderMapper orderMapper;
    private final IoTdbRestClient ioTdbClient;
    private final DeviceReportingProperties properties;
    private final ObjectMapper objectMapper;

    /** 去重窗口：deviceNo+seq -> 过期时间戳（毫秒）。 */
    private final Map<String, Long> dedupeCache = new ConcurrentHashMap<>();

    public DeviceReportingService(BizDeviceCredentialMapper credentialMapper,
                                 BizDeviceOrderReportMapper orderReportMapper,
                                 BizDeviceImageMapper imageMapper,
                                 BizDeviceReportStagingMapper stagingMapper,
                                 BizDeviceAccessLogMapper accessLogMapper,
                                 BizOrderMapper orderMapper,
                                 IoTdbRestClient ioTdbClient,
                                 DeviceReportingProperties properties,
                                 ObjectMapper objectMapper) {
        this.credentialMapper = credentialMapper;
        this.orderReportMapper = orderReportMapper;
        this.imageMapper = imageMapper;
        this.stagingMapper = stagingMapper;
        this.accessLogMapper = accessLogMapper;
        this.orderMapper = orderMapper;
        this.ioTdbClient = ioTdbClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一条上行报文。
     *
     * @param envelope  报文信封
     * @param transport 接入方式：MQTT/COAP/HTTP
     * @return 回执
     */
    @Transactional
    public DeviceAck handle(DeviceEnvelope envelope, String transport) {
        long seq = envelope.seq() == null ? 0L : envelope.seq();
        try {
            // 验签、重放与去重校验统一在管线内完成，三种接入方式行为一致。
            verify(envelope);
            DeviceAck ack = dispatch(envelope);
            appendLog(envelope, transport, ack);
            return ack;
        } catch (BizException ex) {
            DeviceAck ack = DeviceAck.error(ex.getCode(), ex.getMessage(), seq);
            appendLog(envelope, transport, ack);
            return ack;
        } catch (Exception ex) {
            LOG.warn("设备报文处理失败：{}", ex.getMessage(), ex);
            DeviceAck ack = DeviceAck.error(5001, "服务端异常：" + ex.getMessage(), seq);
            appendLog(envelope, transport, ack);
            return ack;
        }
    }

    /** 按报文类型分发处理。 */
    private DeviceAck dispatch(DeviceEnvelope envelope) {
        long seq = envelope.seq() == null ? 0L : envelope.seq();
        String type = envelope.msgType() == null ? "" : envelope.msgType().trim();
        switch (type) {
            case DeviceEnvelope.TYPE_ORDER:
                return handleOrderReport(envelope, seq);
            case DeviceEnvelope.TYPE_IMAGE:
                return handleImage(envelope, seq);
            case DeviceEnvelope.TYPE_STATUS:
                return handleTimeSeries(envelope, seq, "status");
            case DeviceEnvelope.TYPE_SELFCHECK:
                return handleTimeSeries(envelope, seq, "selfcheck");
            case DeviceEnvelope.TYPE_FAULT:
                return handleTimeSeries(envelope, seq, "fault");
            default:
                throw new BizException(1002, "不支持的报文类型：" + type);
        }
    }

    /**
     * 订单作业数据：按订单号精确匹配后覆盖写入。
     *
     * <p>匹配失败（订单不存在或格式不合法）时数据进暂存表，返回 3001，设备继续
     * 按周期上报。</p>
     */
    private DeviceAck handleOrderReport(DeviceEnvelope envelope, long seq) {
        Long orderNo = matchOrderNo(envelope.orderNo());
        if (orderNo == null) {
            stage(envelope, DeviceEnvelope.TYPE_ORDER);
            throw new BizException(3001, "订单未匹配，已暂存等待人工处理");
        }
        BizOrder order = orderMapper.selectOneById(orderNo);
        if (order == null) {
            stage(envelope, DeviceEnvelope.TYPE_ORDER);
            throw new BizException(3001, "订单不存在：" + envelope.orderNo());
        }
        LocalDateTime now = LocalDateTime.now();
        BizDeviceOrderReport report = new BizDeviceOrderReport();
        report.setOrderNo(orderNo);
        report.setDeviceNo(envelope.deviceNo());
        report.setWorkOrderNo(envelope.payloadText("workOrderNo"));
        report.setReportStatus(envelope.payloadText("status"));
        report.setPrintedLengthMeter(meters(envelope.payloadNumber("printedLengthMeter")));
        report.setProgressMeter(meters(envelope.payloadNumber("progressMeter")));
        report.setProgressPercent(percent(envelope.payloadNumber("progressPercent")));
        report.setInkUsageMl(meters(envelope.payloadNumber("inkUsageMl")));
        report.setInkDeltaMl(meters(envelope.payloadNumber("inkDeltaMl")));
        report.setRemainingMinutes(envelope.payloadInt("remainingMinutes"));
        report.setSpeedMeterPerHour(meters(envelope.payloadNumber("speedMeterPerHour")));
        report.setWidthMeter(meters(envelope.payloadNumber("widthMeter")));
        report.setPassCount(envelope.payloadInt("passCount"));
        report.setDeviceState(envelope.payloadText("deviceState"));
        report.setErrorCode(envelope.payloadText("errorCode"));
        report.setImageUploaded(Boolean.TRUE.equals(envelope.payloadBoolean("imageUploaded")) ? 1 : 0);
        report.setReportedAt(envelope.reportedAt());
        report.setDeviceTimestamp(envelope.timestamp());
        report.setDeviceSeq(envelope.seq());
        report.setCreatedAt(now);
        report.setUpdatedAt(now);

        int updated = orderReportMapper.overwrite(orderNo, report.getDeviceNo(), report.getWorkOrderNo(),
                report.getReportStatus(), report.getPrintedLengthMeter(), report.getProgressMeter(),
                report.getProgressPercent(), report.getInkUsageMl(), report.getInkDeltaMl(),
                report.getRemainingMinutes(), report.getSpeedMeterPerHour(), report.getWidthMeter(),
                report.getPassCount(), report.getDeviceState(), report.getErrorCode(),
                report.getImageUploaded(), report.getReportedAt(), report.getDeviceTimestamp(),
                report.getDeviceSeq(), now);
        if (updated == 0) {
            orderReportMapper.insert(report);
        }
        // 匹配成功后清理同一订单的暂存数据。
        stagingMapper.deleteByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .where("raw_order_no = ?", envelope.orderNo()));
        return DeviceAck.ok(seq, true, null);
    }

    /**
     * 订单图片：一条订单只接受一次，重复上传按 4003 忽略。
     */
    private DeviceAck handleImage(DeviceEnvelope envelope, long seq) {
        Long orderNo = matchOrderNo(envelope.orderNo());
        if (orderNo == null) {
            throw new BizException(4001, "图片订单号无法精确匹配，等待人工处理");
        }
        BizOrder order = orderMapper.selectOneById(orderNo);
        if (order == null) {
            throw new BizException(3001, "订单不存在：" + envelope.orderNo());
        }
        BizDeviceImage existing = imageMapper.selectByOrderNo(orderNo);
        if (existing != null) {
            throw new BizException(4003, "该订单图片已上传，重复上传忽略");
        }
        Long sizeBytes = envelope.payloadInt("sizeBytes") == null ? null
                : envelope.payloadInt("sizeBytes").longValue();
        if (sizeBytes != null && sizeBytes > (long) properties.image.maxSizeMb * 1024 * 1024) {
            throw new BizException(4002, "图片超过 " + properties.image.maxSizeMb + " MB 上限");
        }
        String format = envelope.payloadText("format");
        if (format != null && !properties.image.allowedFormats.contains(format.toLowerCase(Locale.ROOT))) {
            throw new BizException(4002, "不支持的图片格式：" + format);
        }
        LocalDateTime now = LocalDateTime.now();
        BizDeviceImage image = new BizDeviceImage();
        image.setImageId("IMG" + orderNo);
        image.setOrderNo(orderNo);
        image.setDeviceNo(envelope.deviceNo());
        image.setFileName(envelope.payloadText("fileName"));
        image.setFormat(format);
        image.setSizeBytes(sizeBytes);
        image.setWidth(envelope.payloadInt("width"));
        image.setHeight(envelope.payloadInt("height"));
        image.setContentHash(envelope.payloadText("hash"));
        image.setOcrText(envelope.orderNo());
        image.setOcrStatus("MATCHED");
        image.setMatched(1);
        image.setUploadedAt(now);
        image.setCreatedAt(now);
        image.setUpdatedAt(now);
        imageMapper.insert(image);
        return DeviceAck.ok(seq, true, image.getImageId());
    }

    /**
     * 自检、异常/故障与心跳：写入 IoTDB 时序库。
     */
    private DeviceAck handleTimeSeries(DeviceEnvelope envelope, long seq, String kind) {
        Map<String, Object> measurements = new LinkedHashMap<>();
        switch (kind) {
            case "status" -> {
                put(measurements, "online", envelope.payloadBoolean("online"));
                put(measurements, "work_state", envelope.payloadText("workState"));
                put(measurements, "temperature", envelope.payloadNumber("temperature"));
                put(measurements, "ink_level_percent", envelope.payloadNumber("inkLevelPercent"));
                put(measurements, "firmware_version", envelope.payloadText("firmwareVersion"));
                put(measurements, "ip", envelope.payloadText("ip"));
                put(measurements, "rssi", envelope.payloadNumber("rssi"));
            }
            case "selfcheck" -> {
                put(measurements, "duration_ms", envelope.payloadInt("durationMs"));
                JsonNode items = envelope.payload() == null ? null : envelope.payload().get("items");
                if (items != null && items.isArray()) {
                    int warn = 0;
                    for (JsonNode item : items) {
                        String result = item.path("result").asText("");
                        if ("warn".equalsIgnoreCase(result) || "fail".equalsIgnoreCase(result)) {
                            warn++;
                        }
                    }
                    measurements.put("warn_items", warn);
                    measurements.put("total_items", items.size());
                }
            }
            default -> {
                put(measurements, "fault_code", envelope.payloadText("faultCode"));
                put(measurements, "fault_level", envelope.payloadText("level"));
                put(measurements, "fault_message", envelope.payloadText("message"));
            }
        }
        long timestamp = envelope.timestamp() == null ? System.currentTimeMillis() : envelope.timestamp();
        try {
            ioTdbClient.write(envelope.deviceNo(), measurements, timestamp);
        } catch (Exception ex) {
            // 时序写入失败不影响设备上报主流程：审计日志已经记录，问题由运维排查。
            LOG.warn("IoTDB 写入失败，设备 {} 的 {} 数据未落时序库：{}", envelope.deviceNo(), kind, ex.getMessage());
        }
        return DeviceAck.ok(seq, true, null);
    }

    /**
     * 订单号精确匹配。
     *
     * <p>归一化规则：去掉空格与换行；配置了前缀时先去掉前缀；配置了正则时必须
     * 命中。最后要求订单号是可解析的数字并真实存在，否则视为未匹配。</p>
     */
    public Long matchOrderNo(String rawOrderNo) {
        if (rawOrderNo == null) {
            return null;
        }
        String text = rawOrderNo.replaceAll("\\s", "");
        if (text.isEmpty()) {
            return null;
        }
        String prefix = properties.ocr.orderNoPrefix;
        if (prefix != null && !prefix.isBlank() && text.startsWith(prefix)) {
            text = text.substring(prefix.length());
        }
        String pattern = properties.ocr.orderNoPattern;
        if (pattern != null && !pattern.isBlank() && !text.matches(pattern)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 暂存未匹配的数据。 */
    private void stage(DeviceEnvelope envelope, String msgType) {
        BizDeviceReportStaging staging = new BizDeviceReportStaging();
        staging.setDeviceNo(envelope.deviceNo());
        staging.setRawOrderNo(envelope.orderNo() == null ? "" : envelope.orderNo());
        staging.setMsgType(msgType);
        staging.setPayloadJson(envelope.rawPayloadJson() == null ? "" : envelope.rawPayloadJson());
        staging.setDeviceTimestamp(envelope.timestamp());
        staging.setDeviceSeq(envelope.seq());
        staging.setReceivedAt(LocalDateTime.now());
        staging.setCreatedAt(LocalDateTime.now());
        stagingMapper.insert(staging);
    }

    /** 写入接入审计。 */
    private void appendLog(DeviceEnvelope envelope, String transport, DeviceAck ack) {
        BizDeviceAccessLog log = new BizDeviceAccessLog();
        log.setDeviceNo(envelope.deviceNo());
        log.setMsgType(envelope.msgType());
        log.setTransport(transport);
        log.setDeviceSeq(envelope.seq());
        log.setResultCode(ack.code());
        log.setResultMessage(truncate(ack.message(), 255));
        log.setMatched(ack.matched() ? 1 : 0);
        log.setCreatedAt(LocalDateTime.now());
        accessLogMapper.insert(log);
    }

    /**
     * 校验设备凭据并验签。
     *
     * <p>签名算法：HMAC-SHA256(secret, deviceNo + "|" + msgType + "|" + timestamp + "|"
     * + nonce + "|" + payloadJsonCompact)，输出十六进制小写。载荷优先使用设备原文，
     * 服务端重新排序序列化作为兜底。</p>
     */
    public void verify(DeviceEnvelope envelope) {
        if (envelope.deviceNo() == null || envelope.deviceNo().isBlank()) {
            throw new BizException(1001, "报文缺少设备编号");
        }
        BizDeviceCredential credential = credentialMapper.selectByDeviceNo(envelope.deviceNo());
        if (credential == null || credential.getEnabled() == null || credential.getEnabled() != 1) {
            throw new BizException(2004, "设备未登记或已停用：" + envelope.deviceNo());
        }
        if (envelope.timestamp() == null) {
            throw new BizException(1001, "报文缺少时间戳");
        }
        long skew = Math.abs(System.currentTimeMillis() - envelope.timestamp());
        if (skew > properties.payload.replayWindowSeconds * 1000L) {
            throw new BizException(2003, "时间戳偏差过大，判定为重放");
        }
        String dedupeKey = envelope.deviceNo() + "#" + envelope.seq();
        long now = System.currentTimeMillis();
        dedupeCache.entrySet().removeIf(entry -> entry.getValue() < now);
        if (envelope.seq() != null && dedupeCache.putIfAbsent(dedupeKey, now
                + properties.payload.dedupeWindowSeconds * 1000L) != null) {
            throw new BizException(2003, "重复报文，已忽略");
        }
        if (envelope.sign() == null || envelope.sign().isBlank()) {
            throw new BizException(2002, "报文缺少签名");
        }
        String expected = sign(credential.getSecret(), envelope);
        if (!expected.equalsIgnoreCase(envelope.sign().trim())) {
            throw new BizException(2002, "签名校验失败");
        }
    }

    /**
     * 计算报文签名。
     *
     * @param secret   设备一机一密密钥
     * @param envelope 报文信封
     * @return 十六进制小写签名
     */
    public String sign(String secret, DeviceEnvelope envelope) {
        String payloadJson = envelope.rawPayloadJson() != null && !envelope.rawPayloadJson().isBlank()
                ? envelope.rawPayloadJson() : canonicalPayload(envelope.payload());
        String base = envelope.deviceNo() + "|" + envelope.msgType() + "|" + envelope.timestamp() + "|"
                + (envelope.nonce() == null ? "" : envelope.nonce()) + "|" + payloadJson;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BizException(500, "签名计算失败：" + ex.getMessage());
        }
    }

    /** 载荷的规范化紧凑 JSON（键按字典序），用于验签兜底。 */
    private String canonicalPayload(JsonNode payload) {
        if (payload == null) {
            return "{}";
        }
        try {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            payload.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) {
                sorted.set(name, payload.get(name));
            }
            return objectMapper.writeValueAsString(sorted);
        } catch (Exception ex) {
            return payload.toString();
        }
    }

    /** 长度类字段：保留 3 位小数并截断（设备口径）。 */
    private static BigDecimal meters(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.DOWN);
    }

    /** 百分比：保留 2 位小数并截断。 */
    private static BigDecimal percent(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.DOWN);
    }

    /** 测点写入：忽略空值。 */
    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /** 截断字符串。 */
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 供审计与查询使用的当前用户（可能为空）。 */
    public Long currentUserId() {
        return SecurityUtils.userId();
    }
}
