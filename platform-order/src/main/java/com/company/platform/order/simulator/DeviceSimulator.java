package com.company.platform.order.simulator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 设备上报模拟器（联调工具，验证 MQTT 链路）。
 *
 * <p>按《设备接入配置模板与报文示例-v1.0》的信封格式构造报文，用设备一机一密
 * 密钥计算 HMAC-SHA256 签名，然后发布到 Broker 的 {@code device/{deviceNo}/order}
 * Topic，最后打印平台回执。</p>
 *
 * <p>用法：{@code java -cp target/classes com.company.platform.order.simulator.DeviceSimulator
 * <brokerHost> <port> <deviceNo> <secret> <orderNo> <printedLength>}</p>
 */
public final class DeviceSimulator {

    private DeviceSimulator() {
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "192.168.3.44";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1883;
        String deviceNo = args.length > 2 ? args[2] : "EQ-F001-01";
        String secret = args.length > 3 ? args[3] : "demo-secret-EQ-F001-01";
        String orderNo = args.length > 4 ? args[4] : "0";
        double printed = args.length > 5 ? Double.parseDouble(args[5]) : 12.5;

        long timestamp = System.currentTimeMillis();
        long seq = System.currentTimeMillis() % 100000;
        String nonce = "sim" + (seq % 1000);
        String payload = "{\"status\":\"running\",\"printedLengthMeter\":" + printed
                + ",\"progressMeter\":" + printed
                + ",\"progressPercent\":25.50"
                + ",\"inkUsageMl\":120.250,\"inkDeltaMl\":2.100"
                + ",\"remainingMinutes\":30,\"speedMeterPerHour\":38.200"
                + ",\"widthMeter\":1.600,\"passCount\":1"
                + ",\"deviceState\":\"printing\",\"errorCode\":\"\",\"imageUploaded\":false"
                + ",\"printedAt\":" + timestamp + "}";
        String sign = sign(secret, deviceNo, "order", timestamp, nonce, payload);
        String message = "{\"version\":\"v1\",\"msgType\":\"order\",\"deviceNo\":\"" + deviceNo
                + "\",\"orderNo\":\"" + orderNo + "\",\"timestamp\":" + timestamp
                + ",\"seq\":" + seq + ",\"nonce\":\"" + nonce + "\",\"sign\":\"" + sign
                + "\",\"payload\":" + payload + "}";

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(10000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            byte[] clientId = "device-simulator".getBytes(StandardCharsets.UTF_8);
            byte[] variable = new byte[10 + 2 + clientId.length];
            int index = 0;
            variable[index++] = 0;
            variable[index++] = 4;
            System.arraycopy("MQTT".getBytes(StandardCharsets.UTF_8), 0, variable, index, 4);
            index += 4;
            variable[index++] = 4;
            variable[index++] = 0x02;
            variable[index++] = 0;
            variable[index++] = 60;
            variable[index++] = (byte) ((clientId.length >> 8) & 0xFF);
            variable[index++] = (byte) (clientId.length & 0xFF);
            System.arraycopy(clientId, 0, variable, index, clientId.length);
            writePacket(out, 0x10, variable);
            in.read();
            readRemainingLength(in);
            in.read();
            in.read();

            // 订阅平台回执 Topic。
            byte[] ackTopic = ("server/" + deviceNo + "/ack").getBytes(StandardCharsets.UTF_8);
            byte[] subscribe = new byte[5 + ackTopic.length + 1];
            subscribe[0] = 0;
            subscribe[1] = 1;
            subscribe[2] = (byte) ((ackTopic.length >> 8) & 0xFF);
            subscribe[3] = (byte) (ackTopic.length & 0xFF);
            System.arraycopy(ackTopic, 0, subscribe, 4, ackTopic.length);
            subscribe[subscribe.length - 1] = 0;
            writePacket(out, 0x82, subscribe);
            // SUBACK：类型 + 剩余长度 + 载荷（包 ID + 授予 QoS）。
            in.read();
            int subackLength = readRemainingLength(in);
            byte[] suback = new byte[subackLength];
            in.readFully(suback);

            byte[] topic = ("device/" + deviceNo + "/order").getBytes(StandardCharsets.UTF_8);
            byte[] content = message.getBytes(StandardCharsets.UTF_8);
            byte[] body = new byte[2 + topic.length + content.length];
            body[0] = (byte) ((topic.length >> 8) & 0xFF);
            body[1] = (byte) (topic.length & 0xFF);
            System.arraycopy(topic, 0, body, 2, topic.length);
            System.arraycopy(content, 0, body, 2 + topic.length, content.length);
            writePacket(out, 0x30, body);

            // 等待平台回执。
            int type = in.read();
            if (type == 0x30) {
                int length = readRemainingLength(in);
                byte[] ack = new byte[length];
                in.readFully(ack);
                int topicLength = ((ack[0] & 0xFF) << 8) | (ack[1] & 0xFF);
                System.out.println("ACK: " + new String(ack, 2 + topicLength, length - 2 - topicLength,
                        StandardCharsets.UTF_8));
            } else {
                System.out.println("未收到回执，包类型：" + type);
            }
        }
    }

    /** 计算报文签名。 */
    private static String sign(String secret, String deviceNo, String msgType, long timestamp,
                               String nonce, String payload) throws Exception {
        String base = deviceNo + "|" + msgType + "|" + timestamp + "|" + nonce + "|" + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
    }

    /** 写一个固定头报文。 */
    private static void writePacket(DataOutputStream out, int header, byte[] body) throws IOException {
        out.write(header);
        writeRemainingLength(out, body.length);
        out.write(body);
        out.flush();
    }

    /** 写剩余长度。 */
    private static void writeRemainingLength(DataOutputStream out, int length) throws IOException {
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
        } while ((digit & 128) != 0);
        return value;
    }
}
