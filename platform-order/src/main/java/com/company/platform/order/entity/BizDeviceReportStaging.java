package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 未匹配暂存实体，对应单表 {@code t_device_report_staging}。
 *
 * <p>设备上报的订单号无法与真实订单精确匹配时（OCR 未完成、识别失败或订单不
 * 存在），数据先落暂存表；匹配成功后归位到正式表并删除暂存记录。</p>
 */
@Table("t_device_report_staging")
public class BizDeviceReportStaging {

    /** 主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 上报设备编号。 */
    private String deviceNo;
    /** 原始订单号文本（未精确匹配）。 */
    private String rawOrderNo;
    /** 报文类型。 */
    private String msgType;
    /** 原始报文内容。 */
    private String payloadJson;
    /** 设备时间戳：毫秒。 */
    private Long deviceTimestamp;
    /** 设备内单调递增序号。 */
    private Long deviceSeq;
    /** 接收时间。 */
    private LocalDateTime receivedAt;
    /** 创建时间。 */
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

    public String getRawOrderNo() {
        return rawOrderNo;
    }

    public void setRawOrderNo(String rawOrderNo) {
        this.rawOrderNo = rawOrderNo;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Long getDeviceTimestamp() {
        return deviceTimestamp;
    }

    public void setDeviceTimestamp(Long deviceTimestamp) {
        this.deviceTimestamp = deviceTimestamp;
    }

    public Long getDeviceSeq() {
        return deviceSeq;
    }

    public void setDeviceSeq(Long deviceSeq) {
        this.deviceSeq = deviceSeq;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
