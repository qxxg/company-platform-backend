package com.company.platform.customer.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.customer.client.ApprovalClient;
import com.company.platform.customer.entity.BizExpenseClaim;
import com.company.platform.customer.entity.BizLoan;
import com.company.platform.customer.entity.BizLoanRepayment;
import com.company.platform.customer.mapper.BizLoanMapper;
import com.company.platform.customer.mapper.BizLoanRepaymentMapper;
import com.company.platform.customer.web.LoanCreateCommand;
import com.company.platform.customer.web.LoanDisburseCommand;
import com.company.platform.customer.web.LoanPageView;
import com.company.platform.customer.web.LoanRepayCommand;
import com.company.platform.customer.web.LoanRepaymentView;
import com.company.platform.customer.web.LoanSummaryView;
import com.company.platform.customer.web.LoanView;
import com.company.platform.customer.web.OutstandingLoanView;
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
 * 借款与还款服务。
 *
 * <p>业务流程：员工创建借款草稿 → 提交统一审批中心（≤5000 元厂长审批，
 * 超过 5000 元财务审批）→ 审批通过后进入“待放款”→ 财务实际放款后进入
 * “还款中”→ 还款登记直到“已还清”。</p>
 *
 * <p>还款记录只追加：手工还款记 CASH/TRANSFER，报销付款冲销记 OFFSET 并
 * 关联报销单。借款余额由带条件的原子更新维护，并发还款不会把余额减成负数。</p>
 */
@Service
public class LoanService {

    /** 允许的放款方式。 */
    private static final Set<String> DISBURSE_METHODS = Set.of("CASH", "TRANSFER");
    /** 允许的手工还款方式；OFFSET 只能由报销付款内部生成。 */
    private static final Set<String> REPAY_METHODS = Set.of("CASH", "TRANSFER");
    /** 可查看全部单据的角色。 */
    private static final Set<String> ALL_DATA_ROLES = Set.of("ADMIN", "FINANCE");

    private final BizLoanMapper loanMapper;
    private final BizLoanRepaymentMapper repaymentMapper;
    private final ApprovalClient approvalClient;
    private final SnowflakeIdGenerator idGenerator;

    public LoanService(BizLoanMapper loanMapper,
                       BizLoanRepaymentMapper repaymentMapper,
                       ApprovalClient approvalClient,
                       SnowflakeIdGenerator idGenerator) {
        this.loanMapper = loanMapper;
        this.repaymentMapper = repaymentMapper;
        this.approvalClient = approvalClient;
        this.idGenerator = idGenerator;
    }

    /**
     * 创建借款草稿。
     *
     * <p>借款人固定取当前登录人；借款日期不能晚于今天，预计还款日期不能
     * 早于借款日期。借款用途必填，便于审批人判断资金用途。</p>
     */
    @Transactional
    public LoanView create(LoanCreateCommand command) {
        Long amount = command.amount();
        if (amount == null || amount <= 0) {
            throw new BizException(400, "借款金额必须大于 0");
        }
        LocalDate loanDate = command.loanDate() == null ? LocalDate.now() : command.loanDate();
        if (loanDate.isAfter(LocalDate.now())) {
            throw new BizException(400, "借款日期不能晚于今天");
        }
        if (command.expectedRepayDate() != null && command.expectedRepayDate().isBefore(loanDate)) {
            throw new BizException(400, "预计还款日期不能早于借款日期");
        }
        Long userId = SecurityUtils.userId();
        if (userId == null) {
            throw new BizException(403, "机器令牌不能创建借款单，请使用员工账号");
        }
        LocalDateTime now = LocalDateTime.now();
        BizLoan loan = new BizLoan();
        loan.setLoanNo("JK-" + idGenerator.nextId());
        loan.setBorrowerUserId(userId);
        loan.setBorrowerName(displayName());
        loan.setAmount(amount);
        loan.setLoanDate(loanDate);
        loan.setPurpose(command.purpose().trim());
        loan.setExpectedRepayDate(command.expectedRepayDate());
        loan.setFactoryId(command.factoryId() == null ? SecurityUtils.factoryId() : command.factoryId());
        loan.setDepartment(trimToNull(command.department()));
        loan.setStatus("DRAFT");
        loan.setDisbursedBy(null);
        loan.setRepaidAmount(0L);
        loan.setOutstandingAmount(0L);
        loan.setCreatedBy(userId);
        loan.setCreatedAt(now);
        loan.setUpdatedBy(userId);
        loan.setUpdatedAt(now);
        loan.setVersion(0);
        loanMapper.insert(loan);
        return LoanView.from(loan, List.of());
    }

    /**
     * 提交审批。
     *
     * <p>只有草稿或被驳回的借款可以提交；审批中心按金额档位匹配审批人。
     * 审批通过只进入待放款，财务实际放款后才开始计还款。</p>
     */
    @Transactional
    public LoanView submit(long id) {
        BizLoan loan = loanMapper.selectForUpdate(id);
        if (loan == null) {
            throw new BizException(404, "借款单不存在：" + id);
        }
        requireOwnerOrPrivileged(loan);
        if (!"DRAFT".equals(loan.getStatus()) && !"REJECTED".equals(loan.getStatus())) {
            throw new BizException(409, "当前状态不能提交审批：" + LoanView.statusName(loan.getStatus()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loanNo", loan.getLoanNo());
        payload.put("borrowerName", loan.getBorrowerName());
        payload.put("purpose", loan.getPurpose());
        payload.put("expectedRepayDate", loan.getExpectedRepayDate());
        String title = "借款申请：" + loan.getBorrowerName() + "（" + loan.getLoanNo() + "）";
        ApprovalClient.ApprovalInstanceRef instance = approvalClient.submit(
                ApprovalClient.BUSINESS_TYPE_LOAN, loan.getId(), title, loan.getAmount(),
                loan.getFactoryId(), payload, "借款");
        loan.setStatus("PENDING");
        loan.setApprovalInstanceId(instance.id());
        loan.setApprovalNote(null);
        loan.setUpdatedBy(SecurityUtils.userId());
        loan.setUpdatedAt(LocalDateTime.now());
        loanMapper.update(loan);
        return LoanView.from(loan, List.of());
    }

    /**
     * 取消借款单。
     *
     * <p>与报销单一致：草稿直接取消；审批中调用审批中心撤销，由回调同步
     * 状态。不在持有行锁时调用审批中心，避免回调更新同一行造成互相等待。</p>
     */
    public LoanView cancel(long id) {
        BizLoan loan = requireLoan(id);
        requireOwnerOrPrivileged(loan);
        if ("DRAFT".equals(loan.getStatus())) {
            loan.setStatus("CANCELLED");
            loan.setUpdatedBy(SecurityUtils.userId());
            loan.setUpdatedAt(LocalDateTime.now());
            loanMapper.update(loan);
            return LoanView.from(loan, List.of());
        }
        if (!"PENDING".equals(loan.getStatus())) {
            throw new BizException(409, "当前状态不能取消：" + LoanView.statusName(loan.getStatus()));
        }
        if (loan.getApprovalInstanceId() == null) {
            throw new BizException(409, "借款单缺少审批实例，不能撤销");
        }
        approvalClient.cancel(loan.getApprovalInstanceId(), "借款");
        BizLoan latest = requireLoan(id);
        if (!"CANCELLED".equals(latest.getStatus())) {
            throw new BizException(409, "审批已撤销，但借款单状态未同步，请刷新后重试");
        }
        return get(id);
    }

    /** 审批通过回调：审批中 → 待放款。 */
    @Transactional
    public LoanView markApproved(long id, String comment) {
        BizLoan loan = loanMapper.selectForUpdate(id);
        if (loan == null) {
            throw new BizException(404, "借款单不存在：" + id);
        }
        if (!"PENDING".equals(loan.getStatus())) {
            if ("APPROVED".equals(loan.getStatus()) || "DISBURSED".equals(loan.getStatus())
                    || "REPAID".equals(loan.getStatus())) {
                return LoanView.from(loan, List.of());
            }
            throw new BizException(409, "借款单不在审批中，不能审批通过：" + LoanView.statusName(loan.getStatus()));
        }
        loan.setStatus("APPROVED");
        loan.setApprovalNote(trimToNull(comment));
        loan.setUpdatedBy(SecurityUtils.userId());
        loan.setUpdatedAt(LocalDateTime.now());
        loanMapper.update(loan);
        return LoanView.from(loan, List.of());
    }

    /** 审批驳回回调：审批中 → 已驳回。 */
    @Transactional
    public LoanView markRejected(long id, String comment) {
        BizLoan loan = loanMapper.selectForUpdate(id);
        if (loan == null) {
            throw new BizException(404, "借款单不存在：" + id);
        }
        if ("REJECTED".equals(loan.getStatus())) {
            return LoanView.from(loan, List.of());
        }
        if (!"PENDING".equals(loan.getStatus())) {
            throw new BizException(409, "借款单不在审批中，不能驳回：" + LoanView.statusName(loan.getStatus()));
        }
        loan.setStatus("REJECTED");
        loan.setApprovalNote(trimToNull(comment));
        loan.setUpdatedBy(SecurityUtils.userId());
        loan.setUpdatedAt(LocalDateTime.now());
        loanMapper.update(loan);
        return LoanView.from(loan, List.of());
    }

    /** 申请人撤销回调：审批中 → 已取消。 */
    @Transactional
    public LoanView markCancelled(long id) {
        BizLoan loan = loanMapper.selectForUpdate(id);
        if (loan == null) {
            throw new BizException(404, "借款单不存在：" + id);
        }
        // 与报销一致：没有 approval:apply 的调用者只能是借款人本人，防止越权撤销。
        if (!SecurityUtils.hasPermission("approval:apply")) {
            Long currentUserId = SecurityUtils.userId();
            if (currentUserId == null || !currentUserId.equals(loan.getBorrowerUserId())) {
                throw new BizException(403, "只有借款人本人可以撤销该借款单");
            }
        }
        if ("CANCELLED".equals(loan.getStatus())) {
            return LoanView.from(loan, List.of());
        }
        if (!"PENDING".equals(loan.getStatus())) {
            throw new BizException(409, "借款单不在审批中，不能撤销：" + LoanView.statusName(loan.getStatus()));
        }
        loan.setStatus("CANCELLED");
        loan.setUpdatedBy(SecurityUtils.userId());
        loan.setUpdatedAt(LocalDateTime.now());
        loanMapper.update(loan);
        return LoanView.from(loan, List.of());
    }

    /**
     * 财务登记放款。
     *
     * <p>审批通过后全额放款，未还金额初始化为借款金额。当前不支持部分放款；
     * 如后续需要，可将放款金额拆分到独立放款记录。</p>
     */
    @Transactional
    public LoanView disburse(long id, LoanDisburseCommand command) {
        BizLoan loan = loanMapper.selectForUpdate(id);
        if (loan == null) {
            throw new BizException(404, "借款单不存在：" + id);
        }
        if (!"APPROVED".equals(loan.getStatus())) {
            throw new BizException(409, "只有待放款的借款单可以放款，当前状态："
                    + LoanView.statusName(loan.getStatus()));
        }
        String method = command == null || command.method() == null || command.method().isBlank()
                ? "TRANSFER" : command.method().trim().toUpperCase(Locale.ROOT);
        if (!DISBURSE_METHODS.contains(method)) {
            throw new BizException(400, "非法放款方式：" + method);
        }
        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();
        loan.setStatus("DISBURSED");
        loan.setDisburseMethod(method);
        loan.setDisbursedBy(userId);
        loan.setDisbursedAt(now);
        loan.setRepaidAmount(0L);
        loan.setOutstandingAmount(safe(loan.getAmount()));
        loan.setUpdatedBy(userId);
        loan.setUpdatedAt(now);
        loanMapper.update(loan);
        return get(id);
    }

    /**
     * 财务登记手工还款。
     *
     * <p>先锁借款单，再写入还款记录并原子扣减未还金额；还款金额不能超过
     * 未还余额，还清后状态自动变为 REPAID。</p>
     */
    @Transactional
    public LoanView repay(long id, LoanRepayCommand command) {
        BizLoan loan = loanMapper.selectForUpdate(id);
        if (loan == null) {
            throw new BizException(404, "借款单不存在：" + id);
        }
        if (!"DISBURSED".equals(loan.getStatus())) {
            throw new BizException(409, "只有还款中的借款可以登记还款，当前状态："
                    + LoanView.statusName(loan.getStatus()));
        }
        Long amount = command.amount();
        if (amount == null || amount <= 0) {
            throw new BizException(400, "还款金额必须大于 0");
        }
        if (amount > safe(loan.getOutstandingAmount())) {
            throw new BizException(400, "还款金额不能超过未还金额");
        }
        LocalDate repaymentDate = command.repaymentDate() == null ? LocalDate.now() : command.repaymentDate();
        if (repaymentDate.isAfter(LocalDate.now())) {
            throw new BizException(400, "还款日期不能晚于今天");
        }
        String method = command.method() == null || command.method().isBlank()
                ? "TRANSFER" : command.method().trim().toUpperCase(Locale.ROOT);
        if (!REPAY_METHODS.contains(method)) {
            throw new BizException(400, "手工还款方式只能是 CASH 或 TRANSFER");
        }
        registerRepayment(loan, amount, repaymentDate, method, null, null,
                trimToNull(command.remark()));
        return get(id);
    }

    /**
     * 报销付款冲销借款。
     *
     * <p>由 {@link ExpenseClaimService#pay} 在同一事务内调用：锁定借款单 →
     * 校验借款人与报销人一致、余额足够 → 写 OFFSET 还款记录 → 原子扣减未还
     * 金额。冲销不产生现金流水。</p>
     *
     * @param loanId 借款单 ID
     * @param amount 冲销金额，单位分
     * @param claim  正在付款的报销单
     * @param remark 付款备注
     */
    @Transactional
    public void offsetForExpense(long loanId, long amount, BizExpenseClaim claim, String remark) {
        if (amount <= 0) {
            throw new BizException(400, "冲销金额必须大于 0");
        }
        BizLoan loan = loanMapper.selectForUpdate(loanId);
        if (loan == null) {
            throw new BizException(404, "借款单不存在：" + loanId);
        }
        if (!"DISBURSED".equals(loan.getStatus())) {
            throw new BizException(409, "只有还款中的借款可以冲销，当前状态："
                    + LoanView.statusName(loan.getStatus()));
        }
        if (!Objects.equals(loan.getBorrowerUserId(), claim.getClaimantUserId())) {
            throw new BizException(400, "借款人与报销人不一致，不能冲销");
        }
        if (amount > safe(loan.getOutstandingAmount())) {
            throw new BizException(400, "冲销金额不能超过借款未还金额");
        }
        String note = remark == null || remark.isBlank() ? "报销付款冲销" : remark;
        registerRepayment(loan, amount, LocalDate.now(), "OFFSET",
                claim.getId(), claim.getClaimNo(), note);
    }

    /** 某借款人当前可冲销的借款：已放款、未还金额大于 0。 */
    @Transactional(readOnly = true)
    public List<OutstandingLoanView> outstandingForBorrower(Long borrowerUserId) {
        if (borrowerUserId == null) {
            throw new BizException(400, "借款人不能为空");
        }
        List<BizLoan> loans = loanMapper.selectListByQuery(QueryWrapper.create()
                .where("borrower_user_id = ?", borrowerUserId)
                .and("status = 'DISBURSED'")
                .and("outstanding_amount > 0")
                .orderBy("loan_date", true)
                .orderBy("id", true));
        return OutstandingLoanView.from(loans);
    }

    /**
     * 借款单分页。
     *
     * <p>数据范围与报销一致：财务/管理员全部，厂长本厂或本人，其他员工本人。
     * 汇总按筛选条件统计全部匹配记录。</p>
     */
    @Transactional(readOnly = true)
    public LoanPageView page(long pageNumber, long pageSize, String keyword, String status,
                             LocalDate dateFrom, LocalDate dateTo) {
        QueryWrapper query = buildQuery(keyword, status, dateFrom, dateTo);
        query.orderBy("created_at", false).orderBy("id", false);
        Page<BizLoan> page = loanMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<LoanView> records = page.getRecords().stream()
                .map(item -> LoanView.from(item, List.of()))
                .toList();

        List<BizLoan> all = loanMapper.selectListByQuery(buildQuery(keyword, status, dateFrom, dateTo));
        long active = 0, repaid = 0;
        long totalAmount = 0, outstanding = 0, repaidAmount = 0;
        for (BizLoan item : all) {
            long amount = safe(item.getAmount());
            totalAmount = Math.addExact(totalAmount, amount);
            if ("DISBURSED".equals(item.getStatus())) {
                active++;
                outstanding = Math.addExact(outstanding, safe(item.getOutstandingAmount()));
            }
            if ("REPAID".equals(item.getStatus())) {
                repaid++;
            }
            repaidAmount = Math.addExact(repaidAmount, safe(item.getRepaidAmount()));
        }
        LoanSummaryView summary = new LoanSummaryView(all.size(), active, repaid,
                totalAmount, outstanding, repaidAmount);
        return new LoanPageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(), summary);
    }

    /** 借款详情，包含全部还款记录；按数据范围校验可见性。 */
    @Transactional(readOnly = true)
    public LoanView get(long id) {
        BizLoan loan = requireLoan(id);
        requireVisible(loan);
        List<BizLoanRepayment> repayments = repaymentMapper.selectListByQuery(QueryWrapper.create()
                .where("loan_id = ?", id)
                .orderBy("id", true));
        return LoanView.from(loan, repayments.stream().map(LoanRepaymentView::from).toList());
    }

    /**
     * 写入还款记录并原子更新借款余额。
     *
     * <p>调用方必须已经锁定借款单。原子更新返回 0 说明余额或状态在读取后
     * 发生变化，直接抛异常回滚，避免记录与余额不一致。</p>
     */
    private void registerRepayment(BizLoan loan, long amount, LocalDate repaymentDate, String method,
                                   Long expenseClaimId, String expenseClaimNo, String remark) {
        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();
        BizLoanRepayment repayment = new BizLoanRepayment();
        repayment.setRepaymentNo("HK-" + idGenerator.nextId());
        repayment.setLoanId(loan.getId());
        repayment.setLoanNo(loan.getLoanNo());
        repayment.setAmount(amount);
        repayment.setRepaymentDate(repaymentDate);
        repayment.setMethod(method);
        repayment.setExpenseClaimId(expenseClaimId);
        repayment.setExpenseClaimNo(expenseClaimNo);
        repayment.setRemark(remark);
        repayment.setCreatedBy(userId);
        repayment.setCreatedAt(now);
        repaymentMapper.insert(repayment);
        if (loanMapper.addRepayment(loan.getId(), amount) != 1) {
            throw new BizException(409, "借款余额已变化，请刷新后重试");
        }
    }

    /** 读取借款单，不存在时抛 404。 */
    private BizLoan requireLoan(long id) {
        BizLoan loan = loanMapper.selectOneById(id);
        if (loan == null) {
            throw new BizException(404, "借款单不存在：" + id);
        }
        return loan;
    }

    /** 构造带数据范围的查询条件。 */
    private QueryWrapper buildQuery(String keyword, String status, LocalDate dateFrom, LocalDate dateTo) {
        QueryWrapper query = QueryWrapper.create();
        if (!canViewAll()) {
            Long userId = SecurityUtils.userId();
            if (SecurityUtils.hasAnyRole("FACTORY_MANAGER") && SecurityUtils.factoryId() != null) {
                query.and("(borrower_user_id = ? OR factory_id = ?)", userId, SecurityUtils.factoryId());
            } else {
                query.and("borrower_user_id = ?", userId);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            query.and("(loan_no LIKE ? OR borrower_name LIKE ? OR purpose LIKE ?)", like, like, like);
        }
        if (status != null && !status.isBlank()) {
            query.and("status = ?", status.trim().toUpperCase(Locale.ROOT));
        }
        if (dateFrom != null) {
            query.and("loan_date >= ?", dateFrom);
        }
        if (dateTo != null) {
            query.and("loan_date <= ?", dateTo);
        }
        return query;
    }

    /** 校验当前用户是否可查看该借款单。 */
    private void requireVisible(BizLoan loan) {
        if (canViewAll()) {
            return;
        }
        if (Objects.equals(loan.getBorrowerUserId(), SecurityUtils.userId())) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")
                && loan.getFactoryId() != null
                && Objects.equals(loan.getFactoryId(), SecurityUtils.factoryId())) {
            return;
        }
        throw new BizException(403, "无权查看该借款单");
    }

    /** 只有本人或管理员/财务可以提交、取消借款单。 */
    private void requireOwnerOrPrivileged(BizLoan loan) {
        if (Objects.equals(loan.getBorrowerUserId(), SecurityUtils.userId()) || canViewAll()) {
            return;
        }
        throw new BizException(403, "只有借款人本人可以操作该借款单");
    }

    /** 财务/管理员/机器令牌按全组织数据范围处理。 */
    private boolean canViewAll() {
        return SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole(ALL_DATA_ROLES.toArray(String[]::new));
    }

    /** 当前用户显示名；昵称为空时退回登录名。 */
    private String displayName() {
        String nickname = SecurityUtils.nickname();
        return nickname == null || nickname.isBlank() ? SecurityUtils.username() : nickname;
    }

    /** 安全取值，避免空指针。 */
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
