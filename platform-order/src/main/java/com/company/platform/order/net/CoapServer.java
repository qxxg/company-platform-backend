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
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CoAP 接入（最小实现，覆盖设备上行的 PUT 资源）。
 *
 * <p>实现 RFC 7252 的最小子集：解析 4 字节消息头、Uri-Path 选项与载荷，按
 * {@code /device/{deviceNo}/{order|image|status|selfcheck|fault}} 路由到统一处理
 * 管线，并用带 Payload 的 ACK 报文回传回执。</p>
 *
 * <p>设备在无 MQTT 通道时使用该入口；报文格式与 MQTT 完全一致（同一套信封与
 * 签名规则），因此两种接入共享同一套校验与落库逻辑。</p>
 */
@Component
public class CoapServer {

    private static final Logger LOG = LoggerFactory.getLogger(CoapServer.class);

    /** CoAP 方法码：PUT = 0.02。 */
    private static final int CODE_PUT = 2;
    /** 选项编号：Uri-Path。 */
    private static final int OPTION_URI_PATH = 11;
    /** 内容格式：application/json。 */
    private static final int OPTION_CONTENT_FORMAT = 12;
    /** Content-Format 取值：application/json。 */
    private static final int CONTENT_FORMAT_JSON = 50;
    private final DeviceReportingProperties properties;
    private final DeviceReportingService reportingService;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile DatagramSocket socket;
    private Thread worker;

    public CoapServer(DeviceReportingProperties properties,
                      DeviceReportingService reportingService,
                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.reportingService = reportingService;
        this.objectMapper = objectMapper;
    }

    /** 启动 CoAP 监听线程。 */
    @PostConstruct
    public void start() {
        if (!properties.enabled || !properties.coap.enabled) {
            LOG.info("CoAP 接入未启用，跳过监听");
            return;
        }
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(properties.coap.host, properties.coap.port));
            socket.setSoTimeout(2000);
        } catch (SocketException ex) {
            LOG.warn("CoAP 监听端口 {} 绑定失败：{}", properties.coap.port, ex.getMessage());
            return;
        }
        running.set(true);
        worker = new Thread(this::runLoop, "coap-server");
        worker.setDaemon(true);
        worker.start();
        LOG.info("CoAP 服务已监听 {}:{}", properties.coap.host, properties.coap.port);
    }

    /** 停止监听。 */
    @PreDestroy
    public void stop() {
        running.set(false);
        DatagramSocket current = socket;
        if (current != null) {
            current.close();
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    /** 接收循环。 */
    private void runLoop() {
        byte[] buffer = new byte[Math.max(1024, properties.coap.maxPayloadKb * 1024)];
        while (running.get()) {
            DatagramSocket current = socket;
            if (current == null) {
                return;
            }
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                current.receive(packet);
                handle(packet);
            } catch (java.net.SocketTimeoutException ignored) {
                // 超时只是为了让线程周期性检查运行标记。
            } catch (IOException ex) {
                if (running.get()) {
                    LOG.warn("CoAP 接收失败：{}", ex.getMessage());
                }
            }
        }
    }

    /** 处理一个 CoAP 报文并回 ACK。 */
    private void handle(DatagramPacket packet) {
        byte[] data = packet.getData();
        int length = packet.getLength();
        if (length < 4) {
            return;
        }
        int version = (data[0] >> 6) & 0x03;
        int type = (data[0] >> 4) & 0x03;
        int tokenLength = data[0] & 0x0F;
        int code = data[1];
        int messageId = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        if (version != 1) {
            return;
        }
        int offset = 4;
        byte[] token = new byte[tokenLength];
        System.arraycopy(data, offset, token, 0, tokenLength);
        offset += tokenLength;

        List<String> uriPath = new ArrayList<>();
        Integer contentFormat = null;
        int optionNumber = 0;
        while (offset < length) {
            // 0xFF 是载荷标记：其后全部是报文正文，不能再按选项解析。
            if ((data[offset] & 0xFF) == 0xFF) {
                offset++;
                break;
            }
            // CoAP 选项首字节：高 4 位为 delta，低 4 位为长度。
            int first = data[offset++] & 0xFF;
            int optionDelta = (first >> 4) & 0x0F;
            int optionLength = first & 0x0F;
            if (optionDelta == 13) {
                optionDelta = (data[offset++] & 0xFF) + 13;
            } else if (optionDelta == 14) {
                optionDelta = (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF)) + 269;
                offset += 2;
            }
            if (optionLength == 13) {
                optionLength = (data[offset++] & 0xFF) + 13;
            } else if (optionLength == 14) {
                optionLength = (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF)) + 269;
                offset += 2;
            }
            // CoAP 选项号是累计值：当前编号 = 上一个编号 + 增量。
            optionNumber += optionDelta;
            if (optionNumber == OPTION_URI_PATH) {
                uriPath.add(new String(data, offset, optionLength, StandardCharsets.UTF_8));
            } else if (optionNumber == OPTION_CONTENT_FORMAT && optionLength > 0) {
                contentFormat = data[offset] & 0xFF;
            }
            offset += optionLength;
        }
        String payloadJson = offset < length
                ? new String(data, offset, length - offset, StandardCharsets.UTF_8) : "";

        DeviceAck ack;
        try {
            if (code != CODE_PUT) {
                ack = DeviceAck.error(1001, "仅支持 PUT 上报", 0L);
            } else {
                DeviceEnvelope envelope = DeviceMessageParser.parse(payloadJson);
                ack = reportingService.handle(envelope, "COAP");
            }
        } catch (Exception ex) {
            ack = DeviceAck.error(5001, "处理失败：" + ex.getMessage(), 0L);
        }
        respond(packet.getAddress(), packet.getPort(), token, messageId, type, ack, contentFormat);
    }

    /** 回 ACK 报文，载荷为回执 JSON。 */
    private void respond(InetAddress address, int port, byte[] token, int messageId, int type,
                         DeviceAck ack, Integer contentFormat) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("code", ack.code());
            payload.put("message", ack.message());
            payload.put("matched", ack.matched());
            payload.put("seq", ack.seq());
            payload.put("serverTime", ack.serverTime());
            if (ack.imageId() != null) {
                payload.put("imageId", ack.imageId());
            }
            byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

            int header = (1 << 6) | (type << 4) | token.length;
            List<byte[]> options = new ArrayList<>();
            if (contentFormat != null) {
                // 选项 12（Content-Format），长度 1：0xC1 + 取值 50。
                options.add(new byte[]{(byte) 0xC1, (byte) CONTENT_FORMAT_JSON});
            }
            int payloadMarker = 0xFF;
            int size = 4 + token.length + 1 + body.length;
            for (byte[] option : options) {
                size += option.length;
            }
            byte[] data = new byte[size];
            int index = 0;
            data[index++] = (byte) header;
            data[index++] = (byte) (CODE_PUT == 2 ? 0x45 : 0x45);
            data[index++] = (byte) ((messageId >> 8) & 0xFF);
            data[index++] = (byte) (messageId & 0xFF);
            System.arraycopy(token, 0, data, index, token.length);
            index += token.length;
            for (byte[] option : options) {
                System.arraycopy(option, 0, data, index, option.length);
                index += option.length;
            }
            data[index++] = (byte) payloadMarker;
            System.arraycopy(body, 0, data, index, body.length);

            DatagramSocket current = socket;
            if (current != null) {
                current.send(new DatagramPacket(data, data.length, new InetSocketAddress(address, port)));
            }
        } catch (Exception ex) {
            LOG.warn("CoAP 回执发送失败：{}", ex.getMessage());
        }
    }
}
