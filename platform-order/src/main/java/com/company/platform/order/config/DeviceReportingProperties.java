package com.company.platform.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备数据接入配置。
 *
 * <p>字段语义与《设备接入配置模板与报文示例-v1.0》一致，全部可用配置文件覆盖；
 * 这里的取值就是系统初始化默认值，现场按网络与设备情况修改即可，不需要改代码。
 * 密钥与口令建议用环境变量注入，不写死在文件里。</p>
 *
 * <p>配置前缀 {@code iot}，示例见 {@code config/application-iot.yml}。</p>
 */
@Component
@ConfigurationProperties(prefix = "iot")
public class DeviceReportingProperties {

    /** 是否启用设备数据接入总开关。 */
    public boolean enabled = true;

    /** MQTT Broker 配置。 */
    public Mqtt mqtt = new Mqtt();

    /** CoAP 服务配置。 */
    public Coap coap = new Coap();

    /** 报文通用配置。 */
    public Payload payload = new Payload();

    /** 订单图片配置。 */
    public Image image = new Image();

    /** OCR 识别与订单号匹配配置。 */
    public Ocr ocr = new Ocr();

    /** Apache IoTDB 时序存储配置。 */
    public Iotdb iotdb = new Iotdb();

    /** 上报频率与保留周期配置。 */
    public Frequency frequency = new Frequency();

    /** MQTT 接入配置。 */
    public static class Mqtt {
        /** 是否启用 MQTT 订阅。 */
        public boolean enabled = true;
        /** Broker 地址。 */
        public String host = "192.168.3.44";
        /** Broker 端口（TLS 使用 8883）。 */
        public int port = 1883;
        /** 客户端 ID。 */
        public String clientId = "company-platform-order";
        /** 用户名，匿名接入时留空。 */
        public String username = "";
        /** 密码。 */
        public String password = "";
        /** 订阅 QoS。 */
        public int qos = 1;
        /** 保活周期（秒）。 */
        public int keepAliveSeconds = 60;
        /** 断线重连间隔（秒）。 */
        public int reconnectSeconds = 10;
        /** 订单作业数据 Topic 模板。 */
        public String orderTopic = "device/{deviceNo}/order";
        /** 订单图片 Topic 模板。 */
        public String imageTopic = "device/{deviceNo}/image";
        /** 状态/心跳 Topic 模板。 */
        public String statusTopic = "device/{deviceNo}/status";
        /** 自检 Topic 模板。 */
        public String selfcheckTopic = "device/{deviceNo}/selfcheck";
        /** 异常/故障 Topic 模板。 */
        public String faultTopic = "device/{deviceNo}/fault";
        /** 平台回执 Topic 模板。 */
        public String ackTopic = "server/{deviceNo}/ack";
    }

    /** CoAP 接入配置。 */
    public static class Coap {
        /** 是否启用 CoAP 服务。 */
        public boolean enabled = true;
        /** 监听地址。 */
        public String host = "0.0.0.0";
        /** 监听端口。 */
        public int port = 5683;
        /** 单条报文上限（KB）。 */
        public int maxPayloadKb = 64;
    }

    /** 报文通用配置。 */
    public static class Payload {
        /** 报文版本。 */
        public String version = "v1";
        /** 单条非图片报文上限（KB）。 */
        public int maxSizeKb = 128;
        /** 签名算法。 */
        public String signAlgorithm = "HmacSHA256";
        /** 时间戳允许偏差（秒），超出视为重放。 */
        public int replayWindowSeconds = 120;
        /** deviceNo+seq 去重窗口（秒）。 */
        public int dedupeWindowSeconds = 300;
    }

    /** 订单图片配置。 */
    public static class Image {
        /** 单张图片上限（MB）。 */
        public int maxSizeMb = 5;
        /** 允许的图片格式。 */
        public List<String> allowedFormats = new ArrayList<>(List.of("jpg", "jpeg", "png"));
        /** 图片保留天数（0 表示永久）。 */
        public int retentionDays = 1095;
    }

    /** OCR 与订单号匹配配置。 */
    public static class Ocr {
        /** OCR 服务地址；为空时使用报文内携带的识别结果。 */
        public String endpoint = "";
        /** 置信度阈值，低于该值转人工处理。 */
        public double confidenceThreshold = 0.90;
        /** 订单号前缀校验，可为空。 */
        public String orderNoPrefix = "";
        /** 订单号正则校验，可为空；为空表示只做精确匹配。 */
        public String orderNoPattern = "";
    }

    /** Apache IoTDB 配置。 */
    public static class Iotdb {
        /** 是否写入 IoTDB。 */
        public boolean enabled = true;
        /** IoTDB 主机。 */
        public String host = "192.168.3.44";
        /** IoTDB REST 端口。 */
        public int port = 18080;
        /** 用户名。 */
        public String username = "root";
        /** 口令。 */
        public String password = "root";
        /** 时序数据库。 */
        public String database = "root.device";
        /** 设备测点前缀（数据库下的 schema）。 */
        public String schemaPrefix = "device";
        /** 请求超时（秒）。 */
        public int timeoutSeconds = 5;
    }

    /** 上报频率与保留周期配置。 */
    public static class Frequency {
        /** 订单作业数据上报周期（秒）。 */
        public int orderSeconds = 10;
        /** 状态/心跳上报周期（秒）。 */
        public int statusSeconds = 30;
        /** 订单作业数据保留天数（0 表示永久）。 */
        public int orderReportDays = 0;
        /** 订单图片保留天数。 */
        public int imageDays = 1095;
        /** 时序数据保留年数。 */
        public int iotdbYears = 3;
    }
}
