package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 订单图片实体，对应单表 {@code t_device_image}。
 *
 * <p>按订单号唯一：一条订单只接受一次图片，重复上传按 4003 忽略；OCR 识别到
 * 的订单号必须与真实订单精确匹配才算归位。</p>
 */
@Table("t_device_image")
public class BizDeviceImage {

    /** 主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 图片编号。 */
    private String imageId;
    /** 订单号，OCR 精确匹配后写入。 */
    private Long orderNo;
    /** 上报设备编号。 */
    private String deviceNo;
    /** 文件名。 */
    private String fileName;
    /** 图片格式。 */
    private String format;
    /** 文件大小：字节。 */
    private Long sizeBytes;
    /** 像素宽。 */
    private Integer width;
    /** 像素高。 */
    private Integer height;
    /** 内容哈希，用于去重。 */
    private String contentHash;
    /** OCR 识别到的订单号。 */
    private String ocrText;
    /** OCR 状态：PENDING/MATCHED/UNMATCHED/MANUAL。 */
    private String ocrStatus;
    /** 是否已匹配订单。 */
    private Integer matched;
    /** 接收时间。 */
    private LocalDateTime uploadedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public String getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(String ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public Integer getMatched() {
        return matched;
    }

    public void setMatched(Integer matched) {
        this.matched = matched;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
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
