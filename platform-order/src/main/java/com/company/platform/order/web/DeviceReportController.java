package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.order.client.IoTdbRestClient;
import com.company.platform.order.config.DeviceReportingProperties;
import com.company.platform.order.entity.BizDeviceAccessLog;
import com.company.platform.order.entity.BizDeviceCredential;
import com.company.platform.order.entity.BizDeviceImage;
import com.company.platform.order.entity.BizDeviceOrderReport;
import com.company.platform.order.entity.BizDeviceReportStaging;
import com.company.platform.order.mapper.BizDeviceAccessLogMapper;
import com.company.platform.order.mapper.BizDeviceCredentialMapper;
import com.company.platform.order.mapper.BizDeviceImageMapper;
import com.company.platform.order.mapper.BizDeviceOrderReportMapper;
import com.company.platform.order.mapper.BizDeviceReportStagingMapper;
import com.company.platform.order.service.DeviceEnvelope;
import com.company.platform.order.service.DeviceMessageParser;
import com.company.platform.order.service.DeviceReportingService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备数据上报接口。
 *
 * <p>上行入口 {@code POST /api/iot/report} 不需要 OAuth 令牌：设备用一机一密密钥
 * 对报文签名，服务端验签后按报文类型落库（订单作业数据 → MySQL，自检/异常 → IoTDB）。
 * 查询接口供 PC Web 的「设备数据上报」页面使用，需要
 * {@code production:report:read} 权限。</p>
 */
@RestController
@RequestMapping("/api")
@Validated
public class DeviceReportController {

    private final DeviceReportingService reportingService;
    private final DeviceReportingProperties properties;
    private final BizDeviceOrderReportMapper orderReportMapper;
    private final BizDeviceImageMapper imageMapper;
    private final BizDeviceReportStagingMapper stagingMapper;
    private final BizDeviceAccessLogMapper accessLogMapper;
    private final BizDeviceCredentialMapper credentialMapper;
    private final IoTdbRestClient ioTdbClient;

    public DeviceReportController(DeviceReportingService reportingService,
                                  DeviceReportingProperties properties,
                                  BizDeviceOrderReportMapper orderReportMapper,
                                  BizDeviceImageMapper imageMapper,
                                  BizDeviceReportStagingMapper stagingMapper,
                                  BizDeviceAccessLogMapper accessLogMapper,
                                  BizDeviceCredentialMapper credentialMapper,
                                  IoTdbRestClient ioTdbClient) {
        this.reportingService = reportingService;
        this.properties = properties;
        this.orderReportMapper = orderReportMapper;
        this.imageMapper = imageMapper;
        this.stagingMapper = stagingMapper;
        this.accessLogMapper = accessLogMapper;
        this.credentialMapper = credentialMapper;
        this.ioTdbClient = ioTdbClient;
    }

    /**
     * 设备上行报文入口（HTTP 方式，MQTT/CoAP 走同一处理管线）。
     *
     * <p>设备按《设备接入配置模板与报文示例-v1.0》第 4.1 节的信封上报，服务端
     * 验签、去重并按类型落库，返回回执码。</p>
     */
    @PostMapping("/iot/report")
    public ApiResponse<DeviceAck> report(@RequestBody String body) {
        DeviceEnvelope envelope;
        try {
            envelope = DeviceMessageParser.parse(body);
        } catch (Exception ex) {
            return ApiResponse.ok(DeviceAck.error(1001, "报文格式错误：" + ex.getMessage(), 0L));
        }
        // 验签、去重与落库都在 DeviceReportingService 内完成，失败同样写接入审计。
        return ApiResponse.ok(reportingService.handle(envelope, "HTTP"));
    }

    /** 订单作业数据分页：按订单号唯一，最新一次覆盖历史。 */
    @GetMapping("/order/device-reports/page")
    @PreAuthorize("hasAuthority('production:report:read')")
    public ApiResponse<PageResult<DeviceOrderReportView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String deviceNo,
            @RequestParam(required = false) Long orderNo) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (deviceNo != null && !deviceNo.isBlank()) {
            conditions.add("device_no = ?");
            arguments.add(deviceNo.trim());
        }
        if (orderNo != null) {
            conditions.add("order_no = ?");
            arguments.add(orderNo);
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("updated_at", false);
        Page<BizDeviceOrderReport> result = orderReportMapper.paginate(Page.of(page, size), query);
        List<DeviceOrderReportView> records = new ArrayList<>();
        for (BizDeviceOrderReport report : result.getRecords()) {
            records.add(DeviceOrderReportView.from(report));
        }
        return ApiResponse.ok(new PageResult<>(records, result.getTotalRow(), result.getPageNumber(),
                result.getPageSize()));
    }

    /** 订单图片记录分页：一条订单只有一条图片。 */
    @GetMapping("/order/device-reports/images/page")
    @PreAuthorize("hasAuthority('production:report:read')")
    public ApiResponse<PageResult<Map<String, Object>>> images(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Page<BizDeviceImage> result = imageMapper.paginate(Page.of(page, size),
                QueryWrapper.create().orderBy("uploaded_at", false));
        List<Map<String, Object>> records = new ArrayList<>();
        for (BizDeviceImage image : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", image.getId());
            row.put("imageId", image.getImageId());
            row.put("orderNo", image.getOrderNo());
            row.put("deviceNo", image.getDeviceNo());
            row.put("fileName", image.getFileName());
            row.put("format", image.getFormat());
            row.put("sizeBytes", image.getSizeBytes());
            row.put("ocrText", image.getOcrText());
            row.put("ocrStatus", image.getOcrStatus());
            row.put("matched", image.getMatched() != null && image.getMatched() == 1);
            row.put("uploadedAt", image.getUploadedAt());
            records.add(row);
        }
        return ApiResponse.ok(new PageResult<>(records, result.getTotalRow(), result.getPageNumber(),
                result.getPageSize()));
    }

    /** 未匹配暂存分页：订单号精确匹配成功后会自动归位。 */
    @GetMapping("/order/device-reports/staging/page")
    @PreAuthorize("hasAuthority('production:report:read')")
    public ApiResponse<PageResult<Map<String, Object>>> staging(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Page<BizDeviceReportStaging> result = stagingMapper.paginate(Page.of(page, size),
                QueryWrapper.create().orderBy("received_at", false));
        List<Map<String, Object>> records = new ArrayList<>();
        for (BizDeviceReportStaging item : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("deviceNo", item.getDeviceNo());
            row.put("rawOrderNo", item.getRawOrderNo());
            row.put("msgType", item.getMsgType());
            row.put("receivedAt", item.getReceivedAt());
            records.add(row);
        }
        return ApiResponse.ok(new PageResult<>(records, result.getTotalRow(), result.getPageNumber(),
                result.getPageSize()));
    }

    /** 接入审计分页：记录每条上行报文的处理结果。 */
    @GetMapping("/order/device-reports/logs/page")
    @PreAuthorize("hasAuthority('production:report:read')")
    public ApiResponse<PageResult<Map<String, Object>>> logs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String deviceNo) {
        QueryWrapper query = QueryWrapper.create();
        if (deviceNo != null && !deviceNo.isBlank()) {
            query.where("device_no = ?", deviceNo.trim());
        }
        query.orderBy("created_at", false);
        Page<BizDeviceAccessLog> result = accessLogMapper.paginate(Page.of(page, size), query);
        List<Map<String, Object>> records = new ArrayList<>();
        for (BizDeviceAccessLog log : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("deviceNo", log.getDeviceNo());
            row.put("msgType", log.getMsgType());
            row.put("transport", log.getTransport());
            row.put("deviceSeq", log.getDeviceSeq());
            row.put("resultCode", log.getResultCode());
            row.put("resultMessage", log.getResultMessage());
            row.put("matched", log.getMatched() != null && log.getMatched() == 1);
            row.put("createdAt", log.getCreatedAt());
            records.add(row);
        }
        return ApiResponse.ok(new PageResult<>(records, result.getTotalRow(), result.getPageNumber(),
                result.getPageSize()));
    }

    /** 设备接入凭据分页：一机一密密钥只展示掩码。 */
    @GetMapping("/order/device-reports/credentials/page")
    @PreAuthorize("hasAuthority('production:report:read')")
    public ApiResponse<PageResult<Map<String, Object>>> credentials(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Page<BizDeviceCredential> result = credentialMapper.paginate(Page.of(page, size),
                QueryWrapper.create().orderBy("device_no", true));
        List<Map<String, Object>> records = new ArrayList<>();
        for (BizDeviceCredential credential : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", credential.getId());
            row.put("deviceNo", credential.getDeviceNo());
            row.put("secretMasked", mask(credential.getSecret()));
            row.put("enabled", credential.getEnabled() != null && credential.getEnabled() == 1);
            row.put("remark", credential.getRemark());
            row.put("updatedAt", credential.getUpdatedAt());
            records.add(row);
        }
        return ApiResponse.ok(new PageResult<>(records, result.getTotalRow(), result.getPageNumber(),
                result.getPageSize()));
    }

    /** IoTDB 时序数据查询：自检、异常与心跳。 */
    @GetMapping("/order/device-reports/iotdb/{deviceNo}")
    @PreAuthorize("hasAuthority('production:report:read')")
    public ApiResponse<List<Map<String, Object>>> iotdb(@PathVariable String deviceNo,
                                                     @RequestParam(defaultValue = "temperature") String measurement,
                                                     @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(ioTdbClient.query(deviceNo, measurement, limit));
    }

    /** 接入配置概览：展示初始化默认值，便于现场核对。 */
    @GetMapping("/order/device-reports/config")
    @PreAuthorize("hasAuthority('production:report:read')")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", properties.enabled);
        data.put("mqtt", Map.ofEntries(
                Map.entry("enabled", properties.mqtt.enabled),
                Map.entry("host", properties.mqtt.host),
                Map.entry("port", properties.mqtt.port),
                Map.entry("qos", properties.mqtt.qos),
                Map.entry("keepAliveSeconds", properties.mqtt.keepAliveSeconds),
                Map.entry("orderTopic", properties.mqtt.orderTopic),
                Map.entry("imageTopic", properties.mqtt.imageTopic),
                Map.entry("statusTopic", properties.mqtt.statusTopic),
                Map.entry("selfcheckTopic", properties.mqtt.selfcheckTopic),
                Map.entry("faultTopic", properties.mqtt.faultTopic),
                Map.entry("ackTopic", properties.mqtt.ackTopic)));
        data.put("coap", Map.of(
                "enabled", properties.coap.enabled,
                "host", properties.coap.host,
                "port", properties.coap.port));
        data.put("payload", Map.of(
                "version", properties.payload.version,
                "signAlgorithm", properties.payload.signAlgorithm,
                "replayWindowSeconds", properties.payload.replayWindowSeconds,
                "dedupeWindowSeconds", properties.payload.dedupeWindowSeconds));
        data.put("image", Map.of(
                "maxSizeMb", properties.image.maxSizeMb,
                "allowedFormats", properties.image.allowedFormats,
                "retentionDays", properties.image.retentionDays));
        data.put("ocr", Map.of(
                "confidenceThreshold", properties.ocr.confidenceThreshold,
                "orderNoPrefix", properties.ocr.orderNoPrefix,
                "orderNoPattern", properties.ocr.orderNoPattern));
        data.put("iotdb", Map.of(
                "enabled", properties.iotdb.enabled,
                "host", properties.iotdb.host,
                "port", properties.iotdb.port,
                "database", properties.iotdb.database));
        data.put("frequency", Map.of(
                "orderSeconds", properties.frequency.orderSeconds,
                "statusSeconds", properties.frequency.statusSeconds,
                "orderReportDays", properties.frequency.orderReportDays,
                "imageDays", properties.frequency.imageDays,
                "iotdbYears", properties.frequency.iotdbYears));
        return ApiResponse.ok(data);
    }

    /** 密钥掩码：只保留前 4 位与后 2 位。 */
    private static String mask(String secret) {
        if (secret == null || secret.length() <= 6) {
            return "******";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 2);
    }
}
