package com.company.platform.customer.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.entity.BizReceivable;
import com.company.platform.customer.mapper.BizCustomerMapper;
import com.company.platform.customer.mapper.BizReceivableMapper;
import com.company.platform.customer.web.CustomerCreateCommand;
import com.company.platform.customer.web.CustomerCreditView;
import com.company.platform.customer.web.CustomerOrderContext;
import com.company.platform.customer.web.CustomerUpdateCommand;
import com.company.platform.customer.web.CustomerView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户主数据与下单客户上下文服务。
 *
 * <p>数据范围规则：管理员、财务、厂长可查看全部客户；普通销售只能查看和
 * 维护归属自己的客户。前端隐藏菜单不算权限控制，所有读写和下单上下文接口
 * 都会在服务层再次校验归属。</p>
 *
 * <p>客户名称、信用额度、结算方式等审批字段不在普通修改命令中，避免在审批
 * 引擎上线前被绕过。信用额度为 0 表示未授信，订单服务会阻断下单。</p>
 */
@Service
public class CustomerService {

    /** 可跨业务员查看全部客户的内部角色。 */
    private static final Set<String> ALL_DATA_ROLES = Set.of("ADMIN", "FINANCE");
    /** 允许的客户状态。 */
    private static final Set<String> CUSTOMER_STATUSES =
            Set.of("PROSPECT", "COOPERATING", "SUSPENDED", "LOST", "BLACKLIST");
    /** 允许的客户类型。 */
    private static final Set<String> CUSTOMER_TYPES = Set.of("ENTERPRISE", "INDIVIDUAL", "OTHER");
    /** 允许的风险等级。 */
    private static final Set<String> RISK_LEVELS = Set.of("NORMAL", "WATCH", "HIGH");
    /** 客户编码日期部分。 */
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BizCustomerMapper customerMapper;
    private final BizReceivableMapper receivableMapper;

    public CustomerService(BizCustomerMapper customerMapper, BizReceivableMapper receivableMapper) {
        this.customerMapper = customerMapper;
        this.receivableMapper = receivableMapper;
    }

    /**
     * 新建客户。
     *
     * <p>销售角色提交的归属业务员会被强制改为当前登录人，防止把客户挂到他人
     * 名下；信用额度也会被归零，必须由财务维护。管理员/财务可以指定归属和额度。</p>
     */
    @Transactional
    public CustomerView create(CustomerCreateCommand command) {
        Long currentUserId = SecurityUtils.userId();
        boolean privileged = canViewAll();
        String name = command.name().trim();
        String creditCode = trimToNull(command.creditCode());
        String phone = trimToNull(command.contactPhone());
        assertNoDuplicate(name, creditCode, phone, null);

        BizCustomer customer = new BizCustomer();
        customer.setCustomerCode(resolveCustomerCode(command.customerCode()));
        customer.setName(name);
        customer.setShortName(trimToNull(command.shortName()));
        customer.setCustomerType(normalizeEnum(command.customerType(), CUSTOMER_TYPES, "ENTERPRISE"));
        customer.setCreditCode(creditCode);
        customer.setIndustry(trimToNull(command.industry()));
        customer.setRegion(trimToNull(command.region()));
        customer.setSource(command.source().trim());
        Long ownerUserId = privileged ? command.ownerUserId() : currentUserId;
        customer.setOwnerUserId(ownerUserId);
        customer.setOwnerName(resolveOwnerName(command.ownerName(), ownerUserId, currentUserId));
        customer.setDepartment(trimToNull(command.department()));
        customer.setContactName(trimToNull(command.contactName()));
        customer.setContactTitle(trimToNull(command.contactTitle()));
        customer.setContactPhone(phone);
        customer.setContactEmail(trimToNull(command.contactEmail()));
        customer.setContactWechat(trimToNull(command.contactWechat()));
        customer.setAddress(trimToNull(command.address()));
        customer.setBillingTitle(trimToNull(command.billingTitle()));
        customer.setTaxNo(trimToNull(command.taxNo()));
        customer.setBankName(trimToNull(command.bankName()));
        customer.setBankAccount(trimToNull(command.bankAccount()));
        customer.setSettlementMethod(defaultText(command.settlementMethod(), "MONTHLY"));
        customer.setPaymentDays(command.paymentDays() == null ? 0 : command.paymentDays());
        customer.setCreditLimit(privileged && command.creditLimit() != null ? command.creditLimit() : 0L);
        customer.setDefaultTaxRate(command.defaultTaxRate() == null
                ? new java.math.BigDecimal("0.1300") : command.defaultTaxRate());
        customer.setCurrency(defaultText(command.currency(), "CNY"));
        customer.setDefaultFactoryId(command.defaultFactoryId());
        String status = normalizeEnum(command.status(), CUSTOMER_STATUSES, "PROSPECT");
        assertStatusMutable(status);
        customer.setStatus(status);
        customer.setRiskLevel(normalizeEnum(command.riskLevel(), RISK_LEVELS, "NORMAL"));
        customer.setFirstCooperationDate(command.firstCooperationDate());
        customer.setRemark(trimToNull(command.remark()));
        customer.setCreatedBy(currentUserId);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedBy(currentUserId);
        customer.setUpdatedAt(customer.getCreatedAt());
        customer.setVersion(0);

        customerMapper.insert(customer);
        return CustomerView.from(customer);
    }

    /** 按 ID 查询客户，并执行数据范围校验。 */
    @Transactional(readOnly = true)
    public CustomerView get(long id) {
        return CustomerView.from(requireAccessible(id));
    }

    /**
     * 修改客户的非审批字段。
     *
     * <p>归属业务员、客户名称、信用额度、结算方式和开票信息属于审批字段，
     * 不在修改命令中；审批中心接入后再开放变更单。</p>
     */
    @Transactional
    public CustomerView update(long id, CustomerUpdateCommand command) {
        BizCustomer customer = requireAccessible(id);
        String creditCode = trimToNull(command.creditCode());
        String phone = trimToNull(command.contactPhone());
        assertNoDuplicate(customer.getName(), creditCode, phone, id);

        customer.setShortName(trimToNull(command.shortName()));
        customer.setCustomerType(normalizeEnum(command.customerType(), CUSTOMER_TYPES, customer.getCustomerType()));
        customer.setCreditCode(creditCode);
        customer.setIndustry(trimToNull(command.industry()));
        customer.setRegion(trimToNull(command.region()));
        customer.setContactName(trimToNull(command.contactName()));
        customer.setContactTitle(trimToNull(command.contactTitle()));
        customer.setContactPhone(phone);
        customer.setContactEmail(trimToNull(command.contactEmail()));
        customer.setContactWechat(trimToNull(command.contactWechat()));
        customer.setAddress(trimToNull(command.address()));
        customer.setDefaultFactoryId(command.defaultFactoryId());
        if (command.status() != null && !command.status().isBlank()) {
            String status = normalizeEnum(command.status(), CUSTOMER_STATUSES, customer.getStatus());
            if (!java.util.Objects.equals(customer.getStatus(), status)) {
                assertStatusChangeAllowed(customer.getStatus(), status);
                assertStatusMutable(status);
                customer.setStatus(status);
            }
        }
        if (command.riskLevel() != null) {
            customer.setRiskLevel(normalizeEnum(command.riskLevel(), RISK_LEVELS, customer.getRiskLevel()));
        }
        customer.setFirstCooperationDate(command.firstCooperationDate());
        customer.setRemark(trimToNull(command.remark()));
        customer.setUpdatedBy(SecurityUtils.userId());
        customer.setUpdatedAt(LocalDateTime.now());
        customerMapper.update(customer);
        return CustomerView.from(customer);
    }

    /**
     * 落地审批通过的信用额度调整。
     *
     * <p>信用额度是审批字段，页面不能直接改数：调用方是审批中心在审批通过后发起，
     * 令牌里带审批人身份，{@code approval:apply} 权限保证只有具备审批执行权限的
     * 角色能落地变更。额度单位为分。</p>
     *
     * @param id          客户 ID
     * @param creditLimit 新的信用额度：分
     */
    @Transactional
    public CustomerView applyCreditLimit(long id, long creditLimit) {
        BizCustomer customer = customerMapper.selectOneById(id);
        if (customer == null) {
            throw new BizException(404, "客户不存在：" + id);
        }
        customer.setCreditLimit(creditLimit);
        customer.setUpdatedBy(SecurityUtils.userId());
        customer.setUpdatedAt(LocalDateTime.now());
        customerMapper.update(customer);
        return CustomerView.from(customer);
    }

    /**
     * 分页查询客户。
     *
     * @param keyword  名称、编码、手机号或信用代码模糊/精确过滤
     * @param status   客户状态
     * @param ownerUserId 归属业务员；销售角色会被强制为当前用户
     */
    @Transactional(readOnly = true)
    public PageResult<CustomerView> page(long pageNumber, long pageSize, String keyword,
                                         String status, Long ownerUserId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(name LIKE ? OR customer_code LIKE ? OR contact_phone LIKE ? OR credit_code = ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
            arguments.add(keyword.trim());
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim());
        }
        if (!canViewAll()) {
            conditions.add("owner_user_id = ?");
            arguments.add(SecurityUtils.userId());
        } else if (ownerUserId != null) {
            conditions.add("owner_user_id = ?");
            arguments.add(ownerUserId);
        }

        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false);
        Page<BizCustomer> page = customerMapper.paginate(Page.of(pageNumber, pageSize), query);
        return new PageResult<>(page.getRecords().stream().map(CustomerView::from).toList(),
                page.getTotalRow(), page.getPageNumber(), page.getPageSize());
    }

    /**
     * 客户信用上下文，只包含应收侧数据。订单侧占用由订单服务汇总。
     */
    @Transactional(readOnly = true)
    public CustomerCreditView credit(long id) {
        BizCustomer customer = requireAccessible(id);
        CreditSnapshot snapshot = buildCreditSnapshot(customer.getId());
        return new CustomerCreditView(customer.getId(), customer.getCreditLimit(), snapshot.outstanding(),
                snapshot.maxOverdueDays(), customer.getPaymentDays(), customer.getStatus(), customer.getRiskLevel());
    }

    /**
     * 下单客户上下文。
     *
     * <p>校验顺序：数据范围、客户存在、客户状态必须为合作中、风险等级不能为高。
     * 信用额度/逾期校验由订单服务完成，因为订单侧还要汇总未发货占用。</p>
     */
    @Transactional(readOnly = true)
    public CustomerOrderContext orderContext(long id) {
        BizCustomer customer = requireForOrder(id);
        CreditSnapshot snapshot = buildCreditSnapshot(customer.getId());
        return new CustomerOrderContext(customer.getId(), customer.getName(), customer.getStatus(),
                customer.getCreditLimit(), snapshot.outstanding(), snapshot.maxOverdueDays());
    }

    /**
     * 获取可访问的客户实体。
     *
     * @throws BizException 404 客户不存在；403 不在当前用户数据范围
     */
    @Transactional(readOnly = true)
    public BizCustomer requireAccessible(long id) {
        BizCustomer customer = customerMapper.selectOneById(id);
        if (customer == null) {
            throw new BizException(404, "客户不存在：" + id);
        }
        if (!canViewAll() && !java.util.Objects.equals(customer.getOwnerUserId(), SecurityUtils.userId())) {
            throw new BizException(403, "无权查看或操作其他业务员的客户");
        }
        return customer;
    }

    /**
     * 获取允许下单的客户。
     *
     * <p>只有“合作中”且非高风险客户允许下单。冻结/黑名单客户的恢复需要审批，
     * 当前阶段直接阻断。</p>
     */
    @Transactional(readOnly = true)
    public BizCustomer requireForOrder(long id) {
        BizCustomer customer = requireAccessible(id);
        if (!"COOPERATING".equals(customer.getStatus())) {
            throw new BizException(409, "客户状态为“" + statusLabel(customer.getStatus()) + "”，当前不允许下单");
        }
        if ("HIGH".equals(customer.getRiskLevel())) {
            throw new BizException(409, "客户为高风险客户，需完成风险审批后才能下单");
        }
        return customer;
    }

    /** 汇总未收应收和最大逾期天数；数据为空时返回 0。 */
    private CreditSnapshot buildCreditSnapshot(long customerId) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ? AND status <> 'CANCELLED'", customerId);
        List<BizReceivable> receivables = receivableMapper.selectListByQuery(query);
        long outstanding = 0L;
        int maxOverdueDays = 0;
        LocalDate today = LocalDate.now();
        for (BizReceivable receivable : receivables) {
            long amount = receivable.getAmount() == null ? 0L : receivable.getAmount();
            long received = receivable.getReceivedAmount() == null ? 0L : receivable.getReceivedAmount();
            long open = Math.max(0L, amount - received);
            outstanding += open;
            if (open > 0 && receivable.getDueDate() != null) {
                long days = ChronoUnit.DAYS.between(receivable.getDueDate(), today);
                if (days > maxOverdueDays) {
                    maxOverdueDays = (int) Math.min(Integer.MAX_VALUE, days);
                }
            }
        }
        return new CreditSnapshot(outstanding, maxOverdueDays);
    }

    /** 当前用户是否可查看全部客户。 */
    public boolean canViewAllData() {
        return canViewAll();
    }

    /** 当前用户是否可查看全部客户。 */
    private boolean canViewAll() {
        // 机器令牌（客户端凭证）代表组织身份，权限完全由 OAuth scope 决定。
        if (SecurityUtils.isServiceAccount()) {
            return true;
        }
        return SecurityUtils.hasAnyRole(ALL_DATA_ROLES.toArray(String[]::new));
    }

    /** 客户名称、信用代码、手机号查重；合并客户需审批，后续单独实现。 */
    private void assertNoDuplicate(String name, String creditCode, String phone, Long excludeId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        conditions.add("name = ?");
        arguments.add(name);
        if (creditCode != null) {
            conditions.add("credit_code = ?");
            arguments.add(creditCode);
        }
        if (phone != null) {
            conditions.add("contact_phone = ?");
            arguments.add(phone);
        }
        StringBuilder where = new StringBuilder("(" + String.join(" OR ", conditions) + ")");
        if (excludeId != null) {
            where.append(" AND id <> ?");
            arguments.add(excludeId);
        }
        long count = customerMapper.selectCountByQuery(
                QueryWrapper.create().where(where.toString(), arguments.toArray()));
        if (count > 0) {
            throw new BizException(409, "存在同名、同信用代码或同手机号的客户，请先查重或走合并审批");
        }
    }

    /** 生成客户编码；调用方也可传入自定义编码。 */
    private String resolveCustomerCode(String requested) {
        String value = trimToNull(requested);
        if (value != null) {
            assertCustomerCodeUnique(value, null);
            return value;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = "C" + LocalDate.now().format(CODE_DATE)
                    + String.format(Locale.ROOT, "%04d", ThreadLocalRandom.current().nextInt(10_000));
            if (customerMapper.selectCountByQuery(QueryWrapper.create().where("customer_code = ?", code)) == 0) {
                return code;
            }
        }
        throw new BizException(500, "客户编码生成失败，请稍后重试");
    }

    /** 自定义编码唯一性校验。 */
    private void assertCustomerCodeUnique(String code, Long excludeId) {
        QueryWrapper query = QueryWrapper.create().where("customer_code = ?", code);
        if (excludeId != null) {
            query.and("id <> ?", excludeId);
        }
        if (customerMapper.selectCountByQuery(query) > 0) {
            throw new BizException(409, "客户编码已存在：" + code);
        }
    }

    /** 黑名单客户只能由管理员/财务创建，普通销售不能直接拉黑。 */
    private void assertStatusMutable(String status) {
        if ("BLACKLIST".equals(status) && !SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            throw new BizException(403, "黑名单状态只能由管理员或财务设置");
        }
    }

    /**
     * 黑名单恢复需要总经理审批；当前审批中心未上线，先收紧为仅管理员可恢复。
     * 其他状态变更允许有 customer:write 权限且通过数据范围的用户操作。
     */
    private void assertStatusChangeAllowed(String previousStatus, String nextStatus) {
        if ("BLACKLIST".equals(previousStatus) && !"BLACKLIST".equals(nextStatus)
                && !SecurityUtils.hasAnyRole("ADMIN")) {
            throw new BizException(403, "黑名单客户恢复合作需总经理审批，当前仅管理员可操作");
        }
    }

    /** 归属人姓名优先使用请求快照，否则使用当前登录人昵称。 */
    private String resolveOwnerName(String requested, Long ownerUserId, Long currentUserId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (java.util.Objects.equals(ownerUserId, currentUserId)) {
            return defaultText(SecurityUtils.nickname(), "用户#" + ownerUserId);
        }
        return "用户#" + ownerUserId;
    }

    /** 枚举字段为空时使用默认值，并校验是否属于允许集合。 */
    private String normalizeEnum(String value, Set<String> allowed, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(400, "非法取值：" + value);
        }
        return normalized;
    }

    /** 去空白，空串转 null。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 默认文本，空值回退。 */
    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    /** 客户状态中文展示，用于阻断提示。 */
    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "PROSPECT" -> "潜在";
            case "COOPERATING" -> "合作中";
            case "SUSPENDED" -> "暂停";
            case "LOST" -> "流失";
            case "BLACKLIST" -> "黑名单";
            default -> status;
        };
    }

    /** 应收汇总的中间结果。 */
    private record CreditSnapshot(long outstanding, int maxOverdueDays) {
    }
}
