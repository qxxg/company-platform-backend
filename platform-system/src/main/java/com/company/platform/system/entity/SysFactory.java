package com.company.platform.system.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 工厂实体，对应 {@code sys_factory}。
 *
 * <p>组织规则为“公司 → 工厂”，工厂是叶子节点，不再下设部门；
 * 每个工厂都有独立仓库，订单只能归属一个工厂。</p>
 */
@Table("sys_factory")
public class SysFactory {

    /** 工厂主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 工厂编码，例如 F001。 */
    private String code;
    /** 工厂名称。 */
    private String name;
    /** 工厂地址。 */
    private String address;
    /** 状态：1 启用，0 停用。 */
    private Integer status;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
