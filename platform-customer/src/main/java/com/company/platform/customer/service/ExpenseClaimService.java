package com.company.platform.customer.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.customer.client.ApprovalClient;
import com.company.platform.customer.entity.BizExpenseClaim;
import com.company.platform.customer.mapper.BizExpenseClaimMapper;
import com.company.platform.customer.web.ExpenseClaimCreateCommand;
import com.company.platform.customer.web.ExpenseClaimPageView;
import com.company.platform.customer.web.ExpenseClaimPayCommand;
import com.company.platform.customer.web.ExpenseClaimSummaryView;
import com.company.platform.customer.web.ExpenseClaimView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 费用报销服务。
 *
 * <p>业务流程：员工创建草稿 → 提交统一审批中心（按金额档位匹配审批人）→
 * 审批通过后进入“待付款”→ 财务登记付款 → 已付款。审批意见由审批中心回调
 * 写入，前端不能直接改状态。</p>
 *
 * <p>金额规则：全部以分存储，后端不做四舍五入；付款时支持用报销人的未还
 * 借款冲销一部分，冲销金额与现金实付之和必须等于报销金额。借款与报销是
 * 独立单据，冲销只会生成一条 OFFSET 还款记录，不会把报销并入借款。</p>
 *
 * <p>数据范围：财务/管理员看全部；厂长看本厂或本人；其他员工只看本人。
 * 页面隐藏菜单不构成权限控制，服务层再次校验。</p>
 */
@Service
public class ExpenseClaimService {

    /** 允许的费用类型。 */
    private static final Set<String> EXPENSE_TYPES = ExpenseClaimView.TYPE_NAMES.keySet();
    /** 允许的付款方式。 */
    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "TRANSFER", "OFFSET");
    /** 可查看全部单据的角色。 */
    private static final Set<String> ALL_DATA_ROLES = Set.of("ADMIN", "FINANCE");

    private final BizExpenseClaimMapper claimMapper;
    private final CustomerService customerService;
    private final LoanService loanService;
    private final ApprovalClient approvalClient;
    private final SnowflakeIdGenerator idGenerator;

    public ExpenseClaimService(BizExpenseClaimMapper claimMapper,
                               CustomerService customerService,
                               LoanService loanService,
                               ApprovalClient approvalClient,
                               SnowflakeIdGenerator idGenerator) {
        this.claimMapper = claimMapper;
        this.customerService = customerService;
        this.loanService = loanService;
        this.approvalClient = approvalClient;
        this.idGenerator = idGenerator;
    }

    /**
     * 创建报销草稿。
     *
     * <p>报销人固定取当前登录人；费用发生日期不能晚于今天。订单、客户、
     * 工厂、部门都是可选归集维度，不参与审批金额计算。</p>
     */
    @Transactional
    public ExpenseClaimView create(ExpenseClaimCreateCommand command) {
        String expenseType = normalizeType(command.expenseType());
        Long amount = command.amount();
        if (amount == null || amount <= 0) {
            throw new BizException(400, "报销金额必须大于 0");
        }
        LocalDate expenseDate = command.expenseDate() == null ? LocalDate.now() : command.expenseDate();
        if (expenseDate.isAfter(LocalDate.now())) {
            throw new BizException(400, "费用发生日期不能晚于今天");
        }
        if (command.customerId() != null) {
            // 引用客户只做存在性与数据范围校验，不改变费用金额。
            customerService.requireAccessible(command.customerId());
        }
        Long userId = SecurityUtils.userId();
        if (userId == null) {
            throw new BizException(403, "机器令牌不能创建报销单，请使用员工账号");
        }
        LocalDateTime now = LocalDateTime.now();
        BizExpenseClaim claim = new BizExpenseClaim();
        claim.setClaimNo("BX-" + idGenerator.nextId());
        claim.setClaimantUserId(userId);
        claim.setClaimantName(displayName());
        claim.setExpenseType(expenseType);
        claim.setAmount(amount);
        claim.setExpenseDate(expenseDate);
        claim.setFactoryId(command.factoryId() == null ? SecurityUtils.factoryId() : command.factoryId());
        claim.setDepartment(trimToNull(command.department()));
        claim.setOrderNo(command.orderNo());
        claim.setCustomerId(command.customerId());
        claim.setDescription(command.description().trim());
        claim.setAttachmentUrl(trimToNull(command.attachmentUrl()));
        claim.setStatus("DRAFT");
        claim.setPaidAmount(0L);
        claim.setOffsetLoanAmount(0L);
        claim.setCreatedBy(userId);
        claim.setCreatedAt(now);
        claim.setUpdatedBy(userId);
        claim.setUpdatedAt(now);
        claim.setVersion(0);
        claimMapper.insert(claim);
        return ExpenseClaimView.from(claim);
    }

    /**
     * 提交审批。
     *
     * <p>只有草稿或被驳回的单据可以重新提交；提交时先调用审批中心，成功后再
     * 把本地状态改为审批中，审批中心不可用时业务事务回滚，不会留下“已提交
     * 但审批中心没有单据”的状态。</p>
     */
    @Transactional
    public ExpenseClaimView submit(long id) {
        BizExpenseClaim claim = claimMapper.selectForUpdate(id);
        if (claim == null) {
            throw new BizException(404, "报销单不存在：" + id);
        }
        requireOwnerOrPrivileged(claim);
        if (!"DRAFT".equals(claim.getStatus()) && !"REJECTED".equals(claim.getStatus())) {
            throw new BizException(409, "当前状态不能提交审批：" + ExpenseClaimView.statusName(claim.getStatus()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("claimNo", claim.getClaimNo());
        payload.put("expenseType", claim.getExpenseType());
        payload.put("expenseTypeName", ExpenseClaimView.typeName(claim.getExpenseType()));
        payload.put("claimantName", claim.getClaimantName());
        payload.put("description", claim.getDescription());
        String title = "费用报销：" + ExpenseClaimView.typeName(claim.getExpenseType())
                + " · " + claim.getClaimantName() + "（" + claim.getClaimNo() + "）";
        ApprovalClient.ApprovalInstanceRef instance = approvalClient.submit(
                ApprovalClient.BUSINESS_TYPE_EXPENSE, claim.getId(), title, claim.getAmount(),
                claim.getFactoryId(), payload, "费用报销");
        claim.setStatus("PENDING");
        claim.setApprovalInstanceId(instance.id());
        claim.setApprovalNote(null);
        claim.setUpdatedBy(SecurityUtils.userId());
        claim.setUpdatedAt(LocalDateTime.now());
        claimMapper.update(claim);
        return ExpenseClaimView.from(claim);
    }

    /**
     * 取消单据。
     *
     * <p>草稿直接取消；审批中的单据调用审批中心撤销，审批中心的回调会把单据
     * 置为已取消。这里不能在持有数据库行锁时调用审批中心，否则回调更新同一行
     * 会与当前事务互相等待。</p>
     */
    public ExpenseClaimView cancel(long id) {
        BizExpenseClaim claim = requireClaim(id);
        requireOwnerOrPrivileged(claim);
        if ("DRAFT".equals(claim.getStatus())) {
            claim.setStatus("CANCELLED");
            claim.setUpdatedBy(SecurityUtils.userId());
            claim.setUpdatedAt(LocalDateTime.now());
            claimMapper.update(claim);
            return ExpenseClaimView.from(claim);
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new BizException(409, "当前状态不能取消：" + ExpenseClaimView.statusName(claim.getStatus()));
        }
        if (claim.getApprovalInstanceId() == null) {
            throw new BizException(409, "报销单缺少审批实例，不能撤销");
        }
        approvalClient.cancel(claim.getApprovalInstanceId(), "费用报销");
        BizExpenseClaim latest = requireClaim(id);
        if (!"CANCELLED".equals(latest.getStatus())) {
            throw new BizException(409, "审批已撤销，但报销单状态未同步，请刷新后重试");
        }
        return ExpenseClaimView.from(latest);
    }

    /**
     * 审批通过回调：审批中 → 待付款。
     *
     * <p>由审批中心携带审批人身份调用，权限在接口层校验为 approval:apply。
     * 重复回调保持幂等，避免网络重试把状态改坏。</p>
     */
    @Transactional
    public ExpenseClaimView markApproved(long id, String comment) {
        BizExpenseClaim claim = claimMapper.selectForUpdate(id);
        if (claim == null) {
            throw new BizException(404, "报销单不存在：" + id);
        }
        if ("APPROVED".equals(claim.getStatus()) || "PAID".equals(claim.getStatus())) {
            return ExpenseClaimView.from(claim);
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new BizException(409, "报销单不在审批中，不能审批通过：" + ExpenseClaimView.statusName(claim.getStatus()));
        }
        claim.setStatus("APPROVED");
        claim.setApprovalNote(trimToNull(comment));
        claim.setUpdatedBy(SecurityUtils.userId());
        claim.setUpdatedAt(LocalDateTime.now());
        claimMapper.update(claim);
        return ExpenseClaimView.from(claim);
    }

    /** 审批驳回回调：审批中 → 已驳回。 */
    @Transactional
    public ExpenseClaimView markRejected(long id, String comment) {
        BizExpenseClaim claim = claimMapper.selectForUpdate(id);
        if (claim == null) {
            throw new BizException(404, "报销单不存在：" + id);
        }
        if ("REJECTED".equals(claim.getStatus())) {
            return ExpenseClaimView.from(claim);
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new BizException(409, "报销单不在审批中，不能驳回：" + ExpenseClaimView.statusName(claim.getStatus()));
        }
        claim.setStatus("REJECTED");
        claim.setApprovalNote(trimToNull(comment));
        claim.setUpdatedBy(SecurityUtils.userId());
        claim.setUpdatedAt(LocalDateTime.now());
        claimMapper.update(claim);
        return ExpenseClaimView.from(claim);
    }

    /** 申请人撤销回调：审批中 → 已取消。 */
    @Transactional
    public ExpenseClaimView markCancelled(long id) {
        BizExpenseClaim claim = claimMapper.selectForUpdate(id);
        if (claim == null) {
            throw new BizException(404, "报销单不存在：" + id);
        }
        /*
         * 撤销回调既会被审批中心以审批人身份调用，也会在员工撤销时携带员工本人令牌调用。
         * 员工只有 approval:submit，没有 approval:apply，因此这里必须补一道归属校验，
         * 防止任何员工用回调地址撤销他人的报销单。
         */
        if (!SecurityUtils.hasPermission("approval:apply")) {
            Long currentUserId = SecurityUtils.userId();
            if (currentUserId == null || !currentUserId.equals(claim.getClaimantUserId())) {
                throw new BizException(403, "只有申请人本人可以撤销该报销单");
            }
        }
        if ("CANCELLED".equals(claim.getStatus())) {
            return ExpenseClaimView.from(claim);
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new BizException(409, "报销单不在审批中，不能撤销：" + ExpenseClaimView.statusName(claim.getStatus()));
        }
        claim.setStatus("CANCELLED");
        claim.setUpdatedBy(SecurityUtils.userId());
        claim.setUpdatedAt(LocalDateTime.now());
        claimMapper.update(claim);
        return ExpenseClaimView.from(claim);
    }

    /**
     * 财务登记付款。
     *
     * <p>付款前先锁报销单；如选择冲销借款，再锁定借款单并生成一条 OFFSET 还款
     * 记录。两步在同一事务内，任一步失败都回滚，不能出现“借款减了、报销没付”
     * 或“报销付了、借款没减”的中间状态。</p>
     */
    @Transactional
    public ExpenseClaimView pay(long id, ExpenseClaimPayCommand command) {
        BizExpenseClaim claim = claimMapper.selectForUpdate(id);
        if (claim == null) {
            throw new BizException(404, "报销单不存在：" + id);
        }
        if (!"APPROVED".equals(claim.getStatus())) {
            throw new BizException(409, "只有待付款的报销单可以登记付款，当前状态："
                    + ExpenseClaimView.statusName(claim.getStatus()));
        }
        long amount = safe(claim.getAmount());
        long offset = command == null || command.offsetLoanAmount() == null ? 0L : command.offsetLoanAmount();
        if (offset < 0) {
            throw new BizException(400, "冲销金额不能为负数");
        }
        if (offset > amount) {
            throw new BizException(400, "冲销金额不能超过报销金额");
        }
        long cash = amount - offset;
        String method = normalizePaymentMethod(command == null ? null : command.paymentMethod(), cash, offset);

        if (offset > 0) {
            if (command == null || command.offsetLoanId() == null) {
                throw new BizException(400, "选择冲销借款时必须指定借款单");
            }
            // 同一事务内锁定借款单并写入 OFFSET 还款记录。
            loanService.offsetForExpense(command.offsetLoanId(), offset, claim,
                    trimToNull(command.remark()));
        }

        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();
        claim.setStatus("PAID");
        claim.setPaidAmount(cash);
        claim.setOffsetLoanAmount(offset);
        claim.setPaymentMethod(method);
        claim.setPaymentRemark(trimToNull(command == null ? null : command.remark()));
        claim.setPaidBy(userId);
        claim.setPaidAt(now);
        claim.setUpdatedBy(userId);
        claim.setUpdatedAt(now);
        claimMapper.update(claim);
        return ExpenseClaimView.from(claim);
    }

    /**
     * 报销单分页。
     *
     * <p>汇总按筛选条件统计全部匹配记录，而不是只统计当前页；财务单据量级
     * 有限，这比再写一套聚合 SQL 更直观，也避免分页合计与列表口径不一致。</p>
     */
    @Transactional(readOnly = true)
    public ExpenseClaimPageView page(long pageNumber, long pageSize, String keyword, String status,
                                     String expenseType, LocalDate dateFrom, LocalDate dateTo) {
        QueryWrapper query = buildQuery(keyword, status, expenseType, dateFrom, dateTo);
        query.orderBy("created_at", false).orderBy("id", false);
        Page<BizExpenseClaim> page = claimMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<ExpenseClaimView> records = page.getRecords().stream().map(ExpenseClaimView::from).toList();

        QueryWrapper summaryQuery = buildQuery(keyword, status, expenseType, dateFrom, dateTo);
        List<BizExpenseClaim> all = claimMapper.selectListByQuery(summaryQuery);
        long draft = 0, pending = 0, approved = 0, paid = 0, rejected = 0;
        long totalAmount = 0, pendingAmount = 0, approvedAmount = 0, paidAmount = 0;
        for (BizExpenseClaim item : all) {
            long amount = safe(item.getAmount());
            totalAmount = Math.addExact(totalAmount, amount);
            switch (item.getStatus()) {
                case "DRAFT" -> draft++;
                case "PENDING" -> {
                    pending++;
                    pendingAmount = Math.addExact(pendingAmount, amount);
                }
                case "APPROVED" -> {
                    approved++;
                    approvedAmount = Math.addExact(approvedAmount, amount);
                }
                case "PAID" -> {
                    paid++;
                    paidAmount = Math.addExact(paidAmount, safe(item.getPaidAmount()));
                }
                case "REJECTED" -> rejected++;
                default -> {
                    // CANCELLED 只计入总数与总额，不进入业务状态统计。
                }
            }
        }
        ExpenseClaimSummaryView summary = new ExpenseClaimSummaryView(all.size(), draft, pending, approved,
                paid, rejected, totalAmount, pendingAmount, approvedAmount, paidAmount);
        return new ExpenseClaimPageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(), summary);
    }

    /** 报销详情；按数据范围校验可见性。 */
    @Transactional(readOnly = true)
    public ExpenseClaimView get(long id) {
        BizExpenseClaim claim = requireClaim(id);
        requireVisible(claim);
        return ExpenseClaimView.from(claim);
    }

    /** 报销人未还借款，供财务付款时选择冲销；仅返回已放款且有余额的借款。 */
    @Transactional(readOnly = true)
    public List<com.company.platform.customer.web.OutstandingLoanView> outstandingLoans(long claimId) {
        BizExpenseClaim claim = requireClaim(claimId);
        requireVisible(claim);
        return loanService.outstandingForBorrower(claim.getClaimantUserId());
    }

    /** 读取报销单，不存在时抛 404。 */
    private BizExpenseClaim requireClaim(long id) {
        BizExpenseClaim claim = claimMapper.selectOneById(id);
        if (claim == null) {
            throw new BizException(404, "报销单不存在：" + id);
        }
        return claim;
    }

    /** 构造带数据范围的查询条件。 */
    private QueryWrapper buildQuery(String keyword, String status, String expenseType,
                                    LocalDate dateFrom, LocalDate dateTo) {
        QueryWrapper query = QueryWrapper.create();
        if (!canViewAll()) {
            Long userId = SecurityUtils.userId();
            if (SecurityUtils.hasAnyRole("FACTORY_MANAGER") && SecurityUtils.factoryId() != null) {
                query.and("(claimant_user_id = ? OR factory_id = ?)", userId, SecurityUtils.factoryId());
            } else {
                query.and("claimant_user_id = ?", userId);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            query.and("(claim_no LIKE ? OR claimant_name LIKE ? OR description LIKE ? OR CAST(order_no AS CHAR) LIKE ?)",
                    like, like, like, like);
        }
        if (status != null && !status.isBlank()) {
            query.and("status = ?", status.trim().toUpperCase(Locale.ROOT));
        }
        if (expenseType != null && !expenseType.isBlank()) {
            query.and("expense_type = ?", expenseType.trim().toUpperCase(Locale.ROOT));
        }
        if (dateFrom != null) {
            query.and("expense_date >= ?", dateFrom);
        }
        if (dateTo != null) {
            query.and("expense_date <= ?", dateTo);
        }
        return query;
    }

    /** 校验当前用户是否可查看该单据。 */
    private void requireVisible(BizExpenseClaim claim) {
        if (canViewAll()) {
            return;
        }
        if (Objects.equals(claim.getClaimantUserId(), SecurityUtils.userId())) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")
                && claim.getFactoryId() != null
                && Objects.equals(claim.getFactoryId(), SecurityUtils.factoryId())) {
            return;
        }
        throw new BizException(403, "无权查看该报销单");
    }

    /** 只有本人或管理员/财务可以提交、取消自己的单据。 */
    private void requireOwnerOrPrivileged(BizExpenseClaim claim) {
        if (Objects.equals(claim.getClaimantUserId(), SecurityUtils.userId()) || canViewAll()) {
            return;
        }
        throw new BizException(403, "只有报销人本人可以操作该单据");
    }

    /** 财务/管理员/机器令牌按全组织数据范围处理。 */
    private boolean canViewAll() {
        return SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole(ALL_DATA_ROLES.toArray(String[]::new));
    }

    /** 校验并规范化费用类型。 */
    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!EXPENSE_TYPES.contains(type)) {
            throw new BizException(400, "非法费用类型：" + value);
        }
        return type;
    }

    /** 付款方式：全额冲销允许 OFFSET；部分现金支付必须是 CASH/TRANSFER。 */
    private String normalizePaymentMethod(String value, long cashAmount, long offsetAmount) {
        String method = value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
        if (cashAmount == 0L) {
            if (method != null && !"OFFSET".equals(method)) {
                throw new BizException(400, "全额冲销时付款方式必须为 OFFSET");
            }
            return "OFFSET";
        }
        if (method == null || method.isBlank()) {
            return "TRANSFER";
        }
        if (!PAYMENT_METHODS.contains(method) || "OFFSET".equals(method)) {
            throw new BizException(400, "存在现金支付时付款方式只能是 CASH 或 TRANSFER");
        }
        return method;
    }

    /** 当前用户显示名；昵称为空时退回登录名。 */
    private String displayName() {
        String nickname = SecurityUtils.nickname();
        return nickname == null || nickname.isBlank() ? SecurityUtils.username() : nickname;
    }

    /** 安全累加，避免空指针。 */
    private static long safe(Long value) {
        return value == null ? 0L : value;
    }

    /** 去掉首尾空白，空串转 null。 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
