package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 设备上行接入审计实体，对应单表 {@code t_device_access_log}。
 *
 * <p>记录每条上行报文的处理结果与回执码，用于设备联调与排障；只追加不修改。</p>
 */
@Table("t_device_access_log")
public class BizDeviceAccessLog {

    /** 主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 设备编号。 */
    private String deviceNo;
    /** 报文类型。 */
    private String msgType;
    /** 接入方式：MQTT/COAP/HTTP。 */
    private String transport;
    /** 设备序号。 */
    private Long deviceSeq;
    /** 回执码：0 成功，1001 格式错误等。 */
    private Integer resultCode;
    /** 结果说明。 */
    private String resultMessage;
    /** 订单是否匹配成功。 */
    private Integer matched;
    /** 接收时间。 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(String deviceNo) {
        this.deviceNo = deviceNo;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public Long getDeviceSeq() {
        return deviceSeq;
    }

    public void setDeviceSeq(Long deviceSeq) {
        this.deviceSeq = deviceSeq;
    }

    public Integer getResultCode() {
        return resultCode;
    }

    public void setResultCode(Integer resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public Integer getMatched() {
        return matched;
    }

    public void setMatched(Integer matched) {
        this.matched = matched;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
