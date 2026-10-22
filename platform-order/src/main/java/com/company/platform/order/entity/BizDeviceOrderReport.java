package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单作业数据实体，对应单表 {@code t_device_order_report}。
 *
 * <p>按订单号唯一：设备每 10 秒上报一次，最新一次覆盖历史，不保留每次推送的
 * 快照。长度类字段保留 3 位小数（设备口径），业务单据按 2 位小数截取。</p>
 */
@Table("t_device_order_report")
public class BizDeviceOrderReport {

    /** 主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 订单号，精确匹配的唯一键。 */
    private Long orderNo;
    /** 上报设备编号。 */
    private String deviceNo;
    /** 工单号。 */
    private String workOrderNo;
    /** 设备侧状态：idle/running/paused/finished。 */
    private String reportStatus;
    /** 累计已打印长度：米，3 位小数。 */
    private BigDecimal printedLengthMeter;
    /** 订单累计完成长度：米，3 位小数。 */
    private BigDecimal progressMeter;
    /** 完成百分比。 */
    private BigDecimal progressPercent;
    /** 累计耗墨量：毫升。 */
    private BigDecimal inkUsageMl;
    /** 本次增量耗墨量：毫升。 */
    private BigDecimal inkDeltaMl;
    /** 预估剩余分钟。 */
    private Integer remainingMinutes;
    /** 当前速度：米/小时。 */
    private BigDecimal speedMeterPerHour;
    /** 当前门幅：米。 */
    private BigDecimal widthMeter;
    /** 已打印遍数。 */
    private Integer passCount;
    /** 设备运行状态快照。 */
    private String deviceState;
    /** 当前异常码。 */
    private String errorCode;
    /** 本订单图片是否已上传。 */
    private Integer imageUploaded;
    /** 设备上报时间。 */
    private LocalDateTime reportedAt;
    /** 设备时间戳：毫秒。 */
    private Long deviceTimestamp;
    /** 设备内单调递增序号。 */
    private Long deviceSeq;
    /** 首次接收时间。 */
    private LocalDateTime createdAt;
    /** 最近覆盖时间。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Long orderNo) {
        this.orderNo = orderNo;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(String deviceNo) {
        this.deviceNo = deviceNo;
    }

    public String getWorkOrderNo() {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo) {
        this.workOrderNo = workOrderNo;
    }

    public String getReportStatus() {
        return reportStatus;
    }

    public void setReportStatus(String reportStatus) {
        this.reportStatus = reportStatus;
    }

    public BigDecimal getPrintedLengthMeter() {
        return printedLengthMeter;
    }

    public void setPrintedLengthMeter(BigDecimal printedLengthMeter) {
        this.printedLengthMeter = printedLengthMeter;
    }

    public BigDecimal getProgressMeter() {
        return progressMeter;
    }

    public void setProgressMeter(BigDecimal progressMeter) {
        this.progressMeter = progressMeter;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
    }

    public BigDecimal getInkUsageMl() {
        return inkUsageMl;
    }

    public void setInkUsageMl(BigDecimal inkUsageMl) {
        this.inkUsageMl = inkUsageMl;
    }

    public BigDecimal getInkDeltaMl() {
        return inkDeltaMl;
    }

    public void setInkDeltaMl(BigDecimal inkDeltaMl) {
        this.inkDeltaMl = inkDeltaMl;
    }

    public Integer getRemainingMinutes() {
        return remainingMinutes;
    }

    public void setRemainingMinutes(Integer remainingMinutes) {
        this.remainingMinutes = remainingMinutes;
    }

    public BigDecimal getSpeedMeterPerHour() {
        return speedMeterPerHour;
    }

    public void setSpeedMeterPerHour(BigDecimal speedMeterPerHour) {
        this.speedMeterPerHour = speedMeterPerHour;
    }

    public BigDecimal getWidthMeter() {
        return widthMeter;
    }

    public void setWidthMeter(BigDecimal widthMeter) {
        this.widthMeter = widthMeter;
    }

    public Integer getPassCount() {
        return passCount;
    }

    public void setPassCount(Integer passCount) {
        this.passCount = passCount;
    }

    public String getDeviceState() {
        return deviceState;
    }

    public void setDeviceState(String deviceState) {
        this.deviceState = deviceState;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Integer getImageUploaded() {
        return imageUploaded;
    }

    public void setImageUploaded(Integer imageUploaded) {
        this.imageUploaded = imageUploaded;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(LocalDateTime reportedAt) {
        this.reportedAt = reportedAt;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
