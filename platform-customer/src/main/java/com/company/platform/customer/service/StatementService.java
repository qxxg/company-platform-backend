package com.company.platform.customer.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.entity.BizInvoice;
import com.company.platform.customer.entity.BizReceiptAllocation;
import com.company.platform.customer.entity.BizReceivable;
import com.company.platform.customer.entity.BizStatement;
import com.company.platform.customer.entity.BizStatementLine;
import com.company.platform.customer.mapper.BizCustomerMapper;
import com.company.platform.customer.mapper.BizInvoiceMapper;
import com.company.platform.customer.mapper.BizReceiptAllocationMapper;
import com.company.platform.customer.mapper.BizReceivableMapper;
import com.company.platform.customer.mapper.BizStatementLineMapper;
import com.company.platform.customer.mapper.BizStatementMapper;
import com.company.platform.customer.web.ReceivableView;
import com.company.platform.customer.web.StatementConfirmCommand;
import com.company.platform.customer.web.StatementGenerateCommand;
import com.company.platform.customer.web.StatementLineView;
import com.company.platform.customer.web.StatementView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 客户对账单服务。
 *
 * <p>按客户和期间生成对账快照：期初余额、本期新增应收、本期核销收款、本期
 * 开票金额和期末余额。期末余额 = 期初 + 本期应收 - 本期核销收款；开票金额
 * 只作为参考，因为开票不等于应收发生。</p>
 *
 * <p>口径说明：</p>
 * <ul>
 *   <li>期初 = 期初之前创建的应收金额 - 期初之前发生的核销金额。</li>
 *   <li>本期收款采用“核销口径”：只有核销到应收的收款才会减少应收余额；
 *       未核销预收不进入对账单，避免期末与应收台账不一致。</li>
 *   <li>已取消应收不进入任何金额，已作废/红冲发票不进入开票金额。</li>
 *   <li>对账单生成后不可修改；客户确认后归档，重新生成会产生新版本。</li>
 * </ul>
 */
@Service
public class StatementService {

    /** 允许的对账单状态。 */
    private static final Set<String> STATUSES = Set.of("GENERATED", "CONFIRMED", "CANCELLED");
    /** 对账期间最大跨度，避免一次生成过多明细。 */
    private static final int MAX_PERIOD_DAYS = 366;

    private final BizStatementMapper statementMapper;
    private final BizStatementLineMapper statementLineMapper;
    private final BizReceivableMapper receivableMapper;
    private final BizReceiptAllocationMapper allocationMapper;
    private final BizInvoiceMapper invoiceMapper;
    private final BizCustomerMapper customerMapper;
    private final CustomerService customerService;

    public StatementService(BizStatementMapper statementMapper,
                            BizStatementLineMapper statementLineMapper,
                            BizReceivableMapper receivableMapper,
                            BizReceiptAllocationMapper allocationMapper,
                            BizInvoiceMapper invoiceMapper,
                            BizCustomerMapper customerMapper,
                            CustomerService customerService) {
        this.statementMapper = statementMapper;
        this.statementLineMapper = statementLineMapper;
        this.receivableMapper = receivableMapper;
        this.allocationMapper = allocationMapper;
        this.invoiceMapper = invoiceMapper;
        this.customerMapper = customerMapper;
        this.customerService = customerService;
    }

    /**
     * 生成客户对账单。
     *
     * <p>步骤：校验期间和数据范围 → 计算期初/本期发生额 → 写入对账单快照 →
     * 写入应收、核销、开票明细。整个生成过程在同一事务内完成，避免出现
     * 只有表头没有明细的中间状态。</p>
     */
    @Transactional
    public StatementView generate(StatementGenerateCommand command) {
        BizCustomer customer = customerService.requireAccessible(command.customerId());
        LocalDate periodStart = command.periodStart();
        LocalDate periodEnd = command.periodEnd();
        if (periodStart == null || periodEnd == null) {
            throw new BizException(400, "对账期间不能为空");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new BizException(400, "对账期间开始日不能晚于结束日");
        }
        if (periodEnd.isAfter(LocalDate.now())) {
            throw new BizException(400, "对账期间结束日不能晚于今天");
        }
        if (ChronoUnit.DAYS.between(periodStart, periodEnd) + 1 > MAX_PERIOD_DAYS) {
            throw new BizException(400, "单次对账期间不能超过 " + MAX_PERIOD_DAYS + " 天");
        }

        LocalDateTime start = periodStart.atStartOfDay();
        LocalDateTime endExclusive = periodEnd.plusDays(1).atStartOfDay();

        // 期初：期初之前创造的应收 - 期初之前发生的核销。
        List<BizReceivable> before = receivableMapper.selectListByQuery(QueryWrapper.create()
                .where("customer_id = ? AND status <> 'CANCELLED' AND created_at < ?",
                        customer.getId(), start));
        long opening = sumReceivables(before) - allocationMapper.sumAllocatedBefore(customer.getId(), start);

        // 本期新增应收。
        List<BizReceivable> inPeriod = receivableMapper.selectListByQuery(QueryWrapper.create()
                .where("customer_id = ? AND status <> 'CANCELLED' AND created_at >= ? AND created_at < ?",
                        customer.getId(), start, endExclusive));
        long receivableAmount = sumReceivables(inPeriod);

        // 本期核销收款。
        List<BizReceiptAllocation> allocations = allocationMapper.selectInPeriod(customer.getId(), start, endExclusive);
        long receivedAmount = sumAllocations(allocations);

        // 本期开票；作废/红冲不计入参考金额。
        List<BizInvoice> invoices = invoiceMapper.selectListByQuery(QueryWrapper.create()
                .where("customer_id = ? AND invoice_date >= ? AND invoice_date <= ? "
                                + "AND status NOT IN ('VOID', 'REVERSED')",
                        customer.getId(), periodStart, periodEnd));
        long invoiceAmount = sumInvoices(invoices);

        long closing = opening + receivableAmount - receivedAmount;

        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();
        BizStatement statement = new BizStatement();
        statement.setCustomerId(customer.getId());
        statement.setPeriodStart(periodStart);
        statement.setPeriodEnd(periodEnd);
        statement.setOpeningAmount(opening);
        statement.setReceivableAmount(receivableAmount);
        statement.setReceivedAmount(receivedAmount);
        statement.setInvoiceAmount(invoiceAmount);
        statement.setClosingAmount(closing);
        statement.setStatus("GENERATED");
        statement.setRemark(trimToNull(command.remark()));
        statement.setCreatedBy(userId);
        statement.setCreatedAt(now);
        statement.setUpdatedBy(userId);
        statement.setUpdatedAt(now);
        statement.setVersion(0);
        statementMapper.insert(statement);

        List<BizStatementLine> lines = new ArrayList<>();
        for (BizReceivable item : inPeriod) {
            lines.add(line(statement.getId(), "RECEIVABLE", item.getId(),
                    ReceivableView.numberOf(item.getId()), item.getCreatedAt().toLocalDate(),
                    safe(item.getAmount()), "应收登记"));
        }
        for (BizReceiptAllocation item : allocations) {
            String receiptNo = "SK-" + String.format(Locale.ROOT, "%08d", item.getReceiptId());
            lines.add(line(statement.getId(), "RECEIPT", item.getId(),
                    receiptNo + " / " + ReceivableView.numberOf(item.getReceivableId()),
                    item.getCreatedAt().toLocalDate(), safe(item.getAmount()), "收款核销"));
        }
        for (BizInvoice item : invoices) {
            lines.add(line(statement.getId(), "INVOICE", item.getId(), item.getInvoiceNumber(),
                    item.getInvoiceDate(), safe(item.getAmount()), "手工开票：" + nullSafe(item.getInvoiceTitle())));
        }
        lines.sort(Comparator.comparing(BizStatementLine::getBizDate)
                .thenComparing(line -> typeOrder(line.getLineType()))
                .thenComparing(BizStatementLine::getId, Comparator.nullsLast(Long::compareTo)));
        for (BizStatementLine line : lines) {
            statementLineMapper.insert(line);
        }
        return get(statement.getId());
    }

    /**
     * 对账单分页。
     *
     * <p>关键字匹配客户名称/编码和系统单号；期间筛选按“区间有交集”处理，
     * 便于查询跨月的对账单。列表不返回明细。</p>
     */
    @Transactional(readOnly = true)
    public PageResult<StatementView> page(long pageNumber, long pageSize, String keyword, Long customerId,
                                          String status, LocalDate periodFrom, LocalDate periodTo) {
        FilterSpec spec = buildFilter(keyword, customerId, status, periodFrom, periodTo);
        QueryWrapper query = QueryWrapper.create();
        applyWhere(query, spec);
        query.orderBy("period_end", false);
        query.orderBy("id", false);
        Page<BizStatement> page = statementMapper.paginate(Page.of(pageNumber, pageSize), query);
        Map<Long, BizCustomer> customers = loadCustomers(page.getRecords());
        List<StatementView> records = page.getRecords().stream()
                .map(item -> StatementView.from(item, customers.get(item.getCustomerId()), List.of()))
                .toList();
        return new PageResult<>(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize());
    }

    /** 对账单详情，包含生成时固化的明细。 */
    @Transactional(readOnly = true)
    public StatementView get(long id) {
        BizStatement statement = requireStatement(id);
        BizCustomer customer = customerService.requireAccessible(statement.getCustomerId());
        List<StatementLineView> lines = statementLineMapper.selectListByQuery(QueryWrapper.create()
                        .where("statement_id = ?", id)
                        .orderBy("biz_date", true)
                        .orderBy("id", true))
                .stream()
                .map(StatementLineView::from)
                .toList();
        return StatementView.from(statement, customer, lines);
    }

    /**
     * 客户确认对账单。
     *
     * <p>只允许确认一次；确认时记录差异说明和确认人。确认后状态变为
     * {@code CONFIRMED}，作为后续催收/归档依据，不再修改金额。</p>
     */
    @Transactional
    public StatementView confirm(long id, StatementConfirmCommand command) {
        BizStatement statement = requireStatement(id);
        customerService.requireAccessible(statement.getCustomerId());
        if (!"GENERATED".equals(statement.getStatus())) {
            throw new BizException(409, "只有“已生成”状态的对账单可以确认");
        }
        String note = command == null ? null : trimToNull(command.differenceNote());
        int updated = statementMapper.confirm(id, note, SecurityUtils.userId(), LocalDateTime.now());
        if (updated != 1) {
            throw new BizException(409, "对账单状态已变化，请刷新后重试");
        }
        return get(id);
    }

    /** 读取对账单实体；不存在时抛 404。 */
    @Transactional(readOnly = true)
    public BizStatement requireStatement(long id) {
        BizStatement statement = statementMapper.selectOneById(id);
        if (statement == null) {
            throw new BizException(404, "对账单不存在：" + id);
        }
        return statement;
    }

    /** 构造筛选条件；非全量数据角色按客户归属过滤。 */
    private FilterSpec buildFilter(String keyword, Long customerId, String status,
                                   LocalDate periodFrom, LocalDate periodTo) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(EXISTS (SELECT 1 FROM biz_customer c WHERE c.id = biz_statement.customer_id "
                    + "AND (c.name LIKE ? OR c.customer_code LIKE ?)) OR CAST(id AS CHAR) LIKE ? "
                    + "OR CONCAT('DZ-', LPAD(id, 8, '0')) LIKE ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (customerId != null) {
            conditions.add("customer_id = ?");
            arguments.add(customerId);
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(normalized)) {
                throw new BizException(400, "非法对账单状态：" + status);
            }
            conditions.add("status = ?");
            arguments.add(normalized);
        }
        if (periodFrom != null) {
            conditions.add("period_end >= ?");
            arguments.add(periodFrom);
        }
        if (periodTo != null) {
            conditions.add("period_start <= ?");
            arguments.add(periodTo);
        }
        if (!customerService.canViewAllData()) {
            conditions.add("customer_id IN (SELECT id FROM biz_customer WHERE owner_user_id = ?)");
            arguments.add(SecurityUtils.userId());
        }
        return new FilterSpec(conditions.isEmpty() ? null : String.join(" AND ", conditions),
                arguments.toArray());
    }

    /** 应用筛选条件。 */
    private void applyWhere(QueryWrapper query, FilterSpec spec) {
        if (spec.where() != null) {
            query.where(spec.where(), spec.args());
        }
    }

    /** 批量加载客户。 */
    private Map<Long, BizCustomer> loadCustomers(List<BizStatement> statements) {
        Set<Long> ids = new HashSet<>();
        for (BizStatement item : statements) {
            if (item.getCustomerId() != null) {
                ids.add(item.getCustomerId());
            }
        }
        Map<Long, BizCustomer> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<BizCustomer> customers = customerMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
        for (BizCustomer customer : customers) {
            result.put(customer.getId(), customer);
        }
        return result;
    }

    /** 构造一条对账单明细。 */
    private BizStatementLine line(Long statementId, String type, Long refId, String refNo,
                                  LocalDate bizDate, long amount, String remark) {
        BizStatementLine line = new BizStatementLine();
        line.setStatementId(statementId);
        line.setLineType(type);
        line.setRefId(refId);
        line.setRefNo(refNo);
        line.setBizDate(bizDate);
        line.setAmount(amount);
        line.setRemark(remark);
        line.setCreatedAt(LocalDateTime.now());
        return line;
    }

    /** 明细排序权重：应收、收款、开票。 */
    private int typeOrder(String type) {
        return switch (type) {
            case "RECEIVABLE" -> 0;
            case "RECEIPT" -> 1;
            case "INVOICE" -> 2;
            default -> 3;
        };
    }

    /** 汇总应收金额，空值按 0。 */
    private long sumReceivables(List<BizReceivable> list) {
        long total = 0L;
        for (BizReceivable item : list) {
            total += safe(item.getAmount());
        }
        return total;
    }

    /** 汇总核销金额。 */
    private long sumAllocations(List<BizReceiptAllocation> list) {
        long total = 0L;
        for (BizReceiptAllocation item : list) {
            total += safe(item.getAmount());
        }
        return total;
    }

    /** 汇总开票金额。 */
    private long sumInvoices(List<BizInvoice> list) {
        long total = 0L;
        for (BizInvoice item : list) {
            total += safe(item.getAmount());
        }
        return total;
    }

    /** 空金额按 0。 */
    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    /** null 转空串，避免拼接出 "null"。 */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 生成 IN 占位符。 */
    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    /** 去空白，空串转 null。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 筛选条件与参数。 */
    private record FilterSpec(String where, Object[] args) {
    }
}
