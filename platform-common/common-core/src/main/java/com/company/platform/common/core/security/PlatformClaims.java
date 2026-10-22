package com.company.platform.common.core.security;

/**
 * 平台自定义 JWT 声明名称。
 *
 * <p>认证中心签发、网关和业务服务读取必须使用同一组常量，避免字符串拼写
 * 不一致导致“令牌里有数据但接口取不到”。标准声明（sub、iss、exp、scope）
 * 继续使用 Spring Security 的常量。</p>
 */
public final class PlatformClaims {

    /** 用户主键，Long。客户端凭证令牌没有该声明。 */
    public static final String USER_ID = "user_id";
    /** 登录名。 */
    public static final String USERNAME = "username";
    /** 显示姓名。 */
    public static final String NICKNAME = "nickname";
    /** 员工编号。 */
    public static final String EMPLOYEE_NO = "employee_no";
    /** 所属工厂 ID；总部账号可为空。 */
    public static final String FACTORY_ID = "factory_id";
    /** 角色编码数组，映射为 ROLE_* authority。 */
    public static final String ROLES = "roles";
    /** 权限码数组，映射为原始 authority。 */
    public static final String PERMISSIONS = "permissions";
    /** 客户端标识；客户端凭证令牌的可选声明。 */
    public static final String CLIENT_ID = "client_id";

    private PlatformClaims() {
    }
}
