package com.company.platform.order.net;

import com.company.platform.order.config.DeviceReportingProperties;
import com.company.platform.order.service.DeviceEnvelope;
import com.company.platform.order.service.DeviceMessageParser;
import com.company.platform.order.service.DeviceReportingService;
import com.company.platform.order.web.DeviceAck;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQTT 接入（最小实现，仅覆盖设备上行与平台回执）。
 *
 * <p>实现 MQTT 3.1.1 客户端的 CONNECT / SUBSCRIBE / PUBLISH / PINGREQ 四个动作，
 * 以 QoS 0 订阅设备上行 Topic，避免引入额外客户端依赖；平台回执按同样的 Topic
 * 模板发布到 {@code server/{deviceNo}/ack}。</p>
 *
 * <p>断线后按配置的重连间隔重试；单条报文解析失败只记录日志，不影响后续报文。</p>
 */
@Component
public class MqttSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(MqttSubscriber.class);

    private final DeviceReportingProperties properties;
    private final DeviceReportingService reportingService;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Socket socket;
    private volatile OutputStream output;
    private volatile DataInputStream input;
    private Thread worker;

    public MqttSubscriber(DeviceReportingProperties properties,
                          DeviceReportingService reportingService,
                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.reportingService = reportingService;
        this.objectMapper = objectMapper;
    }

    /** 启动订阅线程。 */
    @PostConstruct
    public void start() {
        if (!properties.enabled || !properties.mqtt.enabled) {
            LOG.info("MQTT 接入未启用，跳过订阅");
            return;
        }
        running.set(true);
        worker = new Thread(this::runLoop, "mqtt-subscriber");
        worker.setDaemon(true);
        worker.start();
    }

    /** 停止订阅线程并关闭连接。 */
    @PreDestroy
    public void stop() {
        running.set(false);
        closeQuietly();
        if (worker != null) {
            worker.interrupt();
        }
    }

    /** 订阅主循环：连接 → 订阅 → 读取报文，断线后重连。 */
    private void runLoop() {
        while (running.get()) {
            try {
                connect();
                subscribe();
                readLoop();
            } catch (Exception ex) {
                LOG.warn("MQTT 连接中断，{} 秒后重连：{}", properties.mqtt.reconnectSeconds, ex.getMessage());
            } finally {
                closeQuietly();
            }
            try {
                Thread.sleep(properties.mqtt.reconnectSeconds * 1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 建立连接并完成 CONNECT/CONNACK 握手。 */
    private void connect() throws IOException {
        Socket client = new Socket();
        client.connect(new InetSocketAddress(properties.mqtt.host, properties.mqtt.port), 5000);
        client.setSoTimeout(Math.max(5000, properties.mqtt.keepAliveSeconds * 1000 / 2));
        this.socket = client;
        this.output = client.getOutputStream();
        this.input = new DataInputStream(client.getInputStream());

        byte[] clientId = properties.mqtt.clientId.getBytes(StandardCharsets.UTF_8);
        boolean hasUser = properties.mqtt.username != null && !properties.mqtt.username.isBlank();
        byte[] username = properties.mqtt.username == null
                ? new byte[0] : properties.mqtt.username.getBytes(StandardCharsets.UTF_8);
        byte[] password = properties.mqtt.password == null
                ? new byte[0] : properties.mqtt.password.getBytes(StandardCharsets.UTF_8);

        // 可变头 10 字节 + 载荷（clientId、username、password 各含 2 字节长度前缀）
        byte[] variable = new byte[10 + 2 + clientId.length
                + (hasUser ? 2 + username.length : 0)
                + (hasUser ? 2 + password.length : 0)];
        int index = 0;
        variable[index++] = 0;
        variable[index++] = 4;
        System.arraycopy("MQTT".getBytes(StandardCharsets.UTF_8), 0, variable, index, 4);
        index += 4;
        variable[index++] = 4;
        // 连接标志：用户名 + 密码 + 清理会话
        variable[index++] = (byte) ((hasUser ? 0x80 : 0) | (hasUser ? 0x40 : 0) | 0x02);
        variable[index++] = (byte) ((properties.mqtt.keepAliveSeconds >> 8) & 0xFF);
        variable[index++] = (byte) (properties.mqtt.keepAliveSeconds & 0xFF);
        variable[index++] = (byte) ((clientId.length >> 8) & 0xFF);
        variable[index++] = (byte) (clientId.length & 0xFF);
        System.arraycopy(clientId, 0, variable, index, clientId.length);
        index += clientId.length;
        if (hasUser) {
            variable[index++] = (byte) ((username.length >> 8) & 0xFF);
            variable[index++] = (byte) (username.length & 0xFF);
            System.arraycopy(username, 0, variable, index, username.length);
            index += username.length;
            variable[index++] = (byte) ((password.length >> 8) & 0xFF);
            variable[index++] = (byte) (password.length & 0xFF);
            System.arraycopy(password, 0, variable, index, password.length);
        }
        byte[] fixed = new byte[variable.length];
        System.arraycopy(variable, 0, fixed, 0, variable.length);
        writePacket(0x10, fixed);

        DataInputStream in = new DataInputStream(socket.getInputStream());
        int type = in.read();
        readRemainingLength(in);
        if (type != 0x20) {
            throw new IOException("MQTT 握手响应异常：" + type);
        }
        // CONNACK 载荷：会话标志（1 字节）+ 返回码（1 字节）。
        in.readUnsignedByte();
        int returnCode = in.readUnsignedByte();
        if (returnCode != 0) {
            throw new IOException("MQTT 连接被拒绝，返回码：" + returnCode);
        }
        this.input = in;
        LOG.info("已连接 MQTT Broker {}:{}", properties.mqtt.host, properties.mqtt.port);
    }

    /** 订阅设备上行 Topic（把 {deviceNo} 换成通配符 +）。 */
    private void subscribe() throws IOException {
        List<String> topics = List.of(
                properties.mqtt.orderTopic,
                properties.mqtt.imageTopic,
                properties.mqtt.statusTopic,
                properties.mqtt.selfcheckTopic,
                properties.mqtt.faultTopic);
        List<byte[]> payload = new ArrayList<>();
        int size = 2;
        for (String topic : topics) {
            byte[] filter = wildcard(topic).getBytes(StandardCharsets.UTF_8);
            byte[] item = new byte[2 + filter.length + 1];
            item[0] = (byte) ((filter.length >> 8) & 0xFF);
            item[1] = (byte) (filter.length & 0xFF);
            System.arraycopy(filter, 0, item, 2, filter.length);
            item[item.length - 1] = (byte) properties.mqtt.qos;
            payload.add(item);
            size += item.length;
        }
        byte[] body = new byte[size];
        body[0] = 0;
        body[1] = 1;
        int index = 2;
        for (byte[] item : payload) {
            System.arraycopy(item, 0, body, index, item.length);
            index += item.length;
        }
        writePacket(0x82, body);
        // 读取 SUBACK，避免字节流错位导致后续 PUBLISH 解析失败。
        DataInputStream in = this.input;
        int type = in.read();
        int length = readRemainingLength(in);
        byte[] ack = new byte[length];
        in.readFully(ack);
        if (type != 0x90) {
            throw new IOException("订阅响应异常：" + type);
        }
        LOG.info("已订阅设备上行 Topic：{}", topics.stream().map(MqttSubscriber::wildcard).toList());
    }

    /** 读取报文循环：处理 PUBLISH，空闲时发送 PINGREQ 保活。 */
    private void readLoop() throws IOException {
        DataInputStream in = this.input;
        while (running.get()) {
            int type;
            int length;
            byte[] body;
            try {
                type = in.read();
                if (type < 0) {
                    throw new IOException("连接被 Broker 关闭");
                }
                length = readRemainingLength(in);
                body = new byte[length];
                in.readFully(body);
            } catch (java.net.SocketTimeoutException ex) {
                // 空闲超过保活周期的一半：发 PINGREQ 保活连接。
                writePacket(0xC0, new byte[0]);
                continue;
            }
            if ((type & 0xF0) == 0x30) {
                handlePublish(body, (type >> 1) & 0x03);
            } else if (type == 0xC0) {
                // PINGRESP：忽略。
                continue;
            }
        }
    }

    /** 处理一条 PUBLISH 报文。 */
    private void handlePublish(byte[] body, int qos) {
        try {
            int topicLength = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
            int offset = 2 + topicLength;
            // QoS 1/2 的 PUBLISH 在 Topic 之后带 2 字节包 ID，需要回 PUBACK。
            if (qos > 0) {
                int packetId = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
                offset += 2;
                writePacket(0x40, new byte[]{(byte) (packetId >> 8), (byte) (packetId & 0xFF)});
            }
            String messageText = new String(body, offset, body.length - offset, StandardCharsets.UTF_8);
            DeviceEnvelope envelope = DeviceMessageParser.parse(messageText);
            DeviceAck ack = reportingService.handle(envelope, "MQTT");
            publishAck(envelope.deviceNo(), ack);
        } catch (Exception ex) {
            LOG.warn("MQTT 报文处理失败：{}", ex.getMessage());
        }
    }

    /** 发布平台回执。 */
    private void publishAck(String deviceNo, DeviceAck ack) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("seq", ack.seq());
            payload.put("code", ack.code());
            payload.put("message", ack.message());
            payload.put("matched", ack.matched());
            if (ack.imageId() != null) {
                payload.put("imageId", ack.imageId());
            }
            payload.put("serverTime", ack.serverTime());
            ObjectNode body = objectMapper.createObjectNode();
            body.put("version", properties.payload.version);
            body.put("msgType", "ack");
            body.put("deviceNo", deviceNo);
            body.put("timestamp", System.currentTimeMillis());
            body.set("payload", payload);
            byte[] topic = properties.mqtt.ackTopic.replace("{deviceNo}", deviceNo == null ? "" : deviceNo)
                    .getBytes(StandardCharsets.UTF_8);
            byte[] content = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            byte[] data = new byte[2 + topic.length + content.length];
            data[0] = (byte) ((topic.length >> 8) & 0xFF);
            data[1] = (byte) (topic.length & 0xFF);
            System.arraycopy(topic, 0, data, 2, topic.length);
            System.arraycopy(content, 0, data, 2 + topic.length, content.length);
            writePacket(0x30, data);
        } catch (Exception ex) {
            LOG.warn("平台回执发布失败：{}", ex.getMessage());
        }
    }

    /** 写一个固定头报文。 */
    private synchronized void writePacket(int header, byte[] body) throws IOException {
        OutputStream out = this.output;
        if (out == null) {
            throw new IOException("MQTT 连接未建立");
        }
        out.write(header);
        writeRemainingLength(out, body.length);
        out.write(body);
        out.flush();
    }

    /** 写剩余长度（MQTT 可变长度整数）。 */
    private static void writeRemainingLength(OutputStream out, int length) throws IOException {
        int value = length;
        do {
            int digit = value % 128;
            value /= 128;
            if (value > 0) {
                digit = digit | 0x80;
            }
            out.write(digit);
        } while (value > 0);
    }

    /** 读剩余长度。 */
    private static int readRemainingLength(DataInputStream in) throws IOException {
        int multiplier = 1;
        int value = 0;
        int digit;
        do {
            digit = in.readUnsignedByte();
            value += (digit & 127) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128) {
                throw new IOException("剩余长度非法");
            }
        } while ((digit & 128) != 0);
        return value;
    }

    /** Topic 模板转 MQTT 通配符。 */
    private static String wildcard(String template) {
        return template.replace("{deviceNo}", "+");
    }

    /** 关闭连接。 */
    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // 关闭失败无需处理。
        }
        socket = null;
        output = null;
    }
}
