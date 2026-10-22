package com.company.platform.system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 平台用户实体，对应认证库 {@code sys_user}。
 *
 * <p>该表既服务于登录认证，也服务于系统服务的用户列表。密码哈希字段标记
 * {@link JsonIgnore}，保证任何接口都不会意外序列化密码。</p>
 */
@Table("sys_user")
public class SysUser {

    /** 用户主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 登录名，全局唯一。 */
    private String username;
    /** BCrypt 密码哈希，禁止通过 API 返回。 */
    @JsonIgnore
    private String passwordHash;
    /** 显示姓名。 */
    private String nickname;
    /** 员工编号。 */
    private String employeeNo;
    /** 所属工厂；总部职能账号为空。 */
    private Long factoryId;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
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
