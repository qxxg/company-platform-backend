package com.company.platform.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 开发环境基础数据初始化器。
 *
 * <p>启动时幂等写入示范工厂、角色、权限和三个种子账号。所有 INSERT 都使用
 * {@code INSERT IGNORE} 或先查询后插入，重复启动不会产生重复数据。</p>
 *
 * <p><strong>生产环境必须</strong>将 {@code app.seed.enabled} 设为 false，
 * 并替换所有默认密码；账号、角色和权限应由系统管理模块维护。</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String adminPassword;
    private final String salesPassword;
    private final String managerPassword;
    private final String financePassword;

    public DataInitializer(JdbcTemplate jdbcTemplate,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.seed.enabled:true}") boolean enabled,
                           @Value("${app.seed.admin-password:Admin@123}") String adminPassword,
                           @Value("${app.seed.sales-password:Sales@123}") String salesPassword,
                           @Value("${app.seed.manager-password:Manager@123}") String managerPassword,
                           @Value("${app.seed.finance-password:Finance@123}") String financePassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.adminPassword = adminPassword;
        this.salesPassword = salesPassword;
        this.managerPassword = managerPassword;
        this.financePassword = financePassword;
    }

    /**
     * 初始化入口。仅在 app.seed.enabled=true 时执行。
     *
     * <p>先创建工厂和角色/权限，再给角色授权，最后创建用户并绑定角色。
     * 顺序不能颠倒，因为用户角色关联需要先拿到角色主键。</p>
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        seedFactory("F001", "示范工厂", "示范工厂地址");
        seedFactory("F002", "第二工厂", "第二工厂地址");

        seedRole("ADMIN", "系统管理员");
        seedRole("SALES", "销售");
        seedRole("FINANCE", "财务");
        seedRole("FACTORY_MANAGER", "厂长");
        seedRole("OPERATOR", "操作员");

        seedPermission("system:user:list", "查看用户", "api");
        seedPermission("system:user:create", "创建用户", "api");
        seedPermission("system:factory:list", "查看工厂", "api");
        seedPermission("order:read", "查看订单", "api");
        seedPermission("order:create", "创建订单", "api");
        seedPermission("order:approve", "订单审批", "api");
        seedPermission("finance:contract:read", "查看合同", "api");
        seedPermission("finance:contract:write", "录入合同", "api");
        seedPermission("finance:credit:read", "查看客户信用", "api");
        seedPermission("finance:receivable:read", "查看应收账款", "api");
        seedPermission("finance:receivable:write", "登记应收账款", "api");
        seedPermission("finance:receipt:read", "查看收款单", "api");
        seedPermission("finance:receipt:write", "登记收款与核销", "api");
        seedPermission("finance:invoice:read", "查看开票记录", "api");
        seedPermission("finance:invoice:write", "登记手工开票", "api");
        seedPermission("finance:statement:read", "查看客户对账单", "api");
        seedPermission("finance:statement:write", "生成与确认对账单", "api");
        seedPermission("finance:expense:read", "查看费用报销", "api");
        seedPermission("finance:expense:write", "创建与提交费用报销", "api");
        seedPermission("finance:expense:pay", "费用报销付款", "api");
        seedPermission("finance:loan:read", "查看借款与还款", "api");
        seedPermission("finance:loan:write", "创建与提交借款", "api");
        seedPermission("finance:loan:disburse", "借款放款", "api");
        seedPermission("finance:loan:repay", "借款还款登记", "api");
        seedPermission("warehouse:read", "查看仓库物料与库存", "api");
        seedPermission("warehouse:manage", "维护仓库与物料档案", "api");
        seedPermission("warehouse:inbound", "入库", "api");
        seedPermission("warehouse:outbound", "出库", "api");
        seedPermission("equipment:read", "查看设备", "api");
        seedPermission("equipment:write", "维护设备", "api");
        seedPermission("equipment:maintenance:read", "查看设备维护工单", "api");
        seedPermission("equipment:maintenance:write", "维护工单与停机安排", "api");
        seedPermission("equipment:inspection:read", "查看点检保养记录", "api");
        seedPermission("equipment:inspection:write", "登记点检保养记录", "api");
        seedPermission("production:schedule:read", "查看排产队列", "api");
        seedPermission("production:schedule:write", "工单排产与队列操作", "api");
        seedPermission("production:queue:jump:apply", "申请设备队列插队", "api");
        seedPermission("production:queue:jump:approve", "审批设备队列插队", "api");
        seedPermission("production:cost:read", "查看工单材料成本", "api");
        seedPermission("production:report:read", "查看设备数据上报", "api");
        seedPermission("customer:read", "查看客户", "api");
        seedPermission("customer:write", "维护客户", "api");
        seedPermission("approval:read", "查看审批", "api");
        seedPermission("approval:approve", "审批", "api");
        seedPermission("approval:submit", "提交审批申请", "api");
        seedPermission("approval:manage", "维护审批流程与档位", "api");
        seedPermission("approval:apply", "执行审批通过后的业务变更", "api");
        seedPermission("ai:assistant:use", "使用 AI 助手", "api");
        seedPermission("bi:dashboard:read", "查看经营看板", "api");
        seedPermission("bi:cockpit:read", "查看经营大屏", "api");
        seedPermission("bi:cost:read", "查看成本毛利指标", "api");
        seedPermission("bi:etl:run", "触发 BI 数据同步", "api");

        Long adminRoleId = roleId("ADMIN");
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) "
                + "SELECT ?, id FROM sys_permission", adminRoleId);

        assignPermissions("SALES", "order:read", "order:create", "system:factory:list",
                "customer:read", "customer:write", "finance:receivable:read",
                "finance:expense:read", "finance:expense:write",
                "finance:loan:read", "finance:loan:write",
                "production:schedule:read", "production:queue:jump:apply",
                "equipment:maintenance:read", "equipment:inspection:read",
                "approval:read", "approval:submit", "ai:assistant:use", "bi:dashboard:read");
        assignPermissions("FACTORY_MANAGER", "order:read", "order:approve", "system:factory:list",
                "equipment:read", "equipment:write", "production:schedule:read",
                "production:schedule:write", "production:queue:jump:approve",
                "equipment:maintenance:read", "equipment:maintenance:write",
                "equipment:inspection:read", "equipment:inspection:write",
                "production:cost:read", "production:report:read",
                "finance:expense:read", "finance:expense:write",
                "finance:loan:read", "finance:loan:write",
                "warehouse:read", "warehouse:manage", "warehouse:inbound", "warehouse:outbound",
                "approval:read", "approval:approve", "approval:apply", "approval:submit",
                "ai:assistant:use", "bi:dashboard:read", "bi:cockpit:read");
        assignPermissions("OPERATOR", "production:schedule:read",
                "equipment:inspection:read", "equipment:inspection:write",
                "finance:expense:read", "finance:expense:write",
                "finance:loan:read", "finance:loan:write",
                "warehouse:read", "warehouse:outbound", "approval:read", "approval:submit",
                "ai:assistant:use", "bi:dashboard:read");
        assignPermissions("FINANCE", "order:read", "finance:contract:read", "finance:contract:write",
                "finance:credit:read", "finance:receivable:read", "finance:receivable:write",
                "finance:receipt:read", "finance:receipt:write",
                "finance:invoice:read", "finance:invoice:write",
                "finance:statement:read", "finance:statement:write", "customer:read",
                "finance:expense:read", "finance:expense:write", "finance:expense:pay",
                "finance:loan:read", "finance:loan:write", "finance:loan:disburse", "finance:loan:repay",
                "equipment:maintenance:read", "production:cost:read", "production:report:read",
                "warehouse:read",
                "approval:read", "approval:approve", "approval:manage", "approval:apply", "approval:submit",
                "ai:assistant:use", "bi:dashboard:read", "bi:cockpit:read", "bi:cost:read");

        Long factoryId = factoryId("F001");
        seedUser("admin", adminPassword, "系统管理员", "A0001", null, "ADMIN");
        seedUser("sales01", salesPassword, "销售一号", "S0001", factoryId, "SALES");
        seedUser("manager01", managerPassword, "厂长一号", "M0001", factoryId, "FACTORY_MANAGER");
        // 财务账号归属总部，不绑定工厂；用于合同、应收和收款模块的权限验证。
        seedUser("finance01", financePassword, "财务一号", "F0001", null, "FINANCE");

        log.info("Default data initialized. Users: admin, sales01, manager01, finance01. "
                + "Change passwords before production.");
    }

    /** 按工厂编码幂等写入工厂。 */
    private void seedFactory(String code, String name, String address) {
        jdbcTemplate.update("INSERT IGNORE INTO sys_factory (code, name, address, status) VALUES (?, ?, ?, 1)",
                code, name, address);
    }

    /** 按角色编码幂等写入角色。 */
    private void seedRole(String code, String name) {
        jdbcTemplate.update("INSERT IGNORE INTO sys_role (code, name, status) VALUES (?, ?, 1)", code, name);
    }

    /** 按权限编码幂等写入权限，parent_id=0 表示一级权限。 */
    private void seedPermission(String code, String name, String type) {
        jdbcTemplate.update("INSERT IGNORE INTO sys_permission (code, name, type, parent_id, status) "
                + "VALUES (?, ?, ?, 0, 1)", code, name, type);
    }

    /** 给角色追加权限，重复执行不会报错。 */
    private void assignPermissions(String roleCode, String... permissionCodes) {
        Long roleId = roleId(roleCode);
        for (String permissionCode : permissionCodes) {
            Long permissionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_permission WHERE code = ?", Long.class, permissionCode);
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                    roleId, permissionId);
        }
    }

    /**
     * 幂等创建用户并绑定角色。已存在的用户保留原密码，避免每次启动重置密码；
     * 生产环境应关闭种子功能，改由账号管理页面创建用户。
     */
    private void seedUser(String username, String rawPassword, String nickname, String employeeNo,
                          Long factoryId, String roleCode) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        Long userId;
        if (ids.isEmpty()) {
            jdbcTemplate.update("INSERT INTO sys_user "
                            + "(username, password_hash, nickname, employee_no, factory_id, status) "
                            + "VALUES (?, ?, ?, ?, ?, 1)",
                    username, passwordEncoder.encode(rawPassword), nickname, employeeNo, factoryId);
            userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        } else {
            userId = ids.get(0);
        }

        Long roleId = roleId(roleCode);
        jdbcTemplate.update("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private Long roleId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE code = ?", Long.class, code);
    }

    private Long factoryId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_factory WHERE code = ?", Long.class, code);
    }
}
