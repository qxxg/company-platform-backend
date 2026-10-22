package com.company.platform.customer.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.customer.entity.BizContract;
import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.entity.BizReceivable;
import com.company.platform.customer.mapper.BizContractMapper;
import com.company.platform.customer.mapper.BizCustomerMapper;
import com.company.platform.customer.mapper.BizReceivableMapper;
import com.company.platform.customer.web.ReceivableCreateCommand;
import com.company.platform.customer.web.ReceivablePageView;
import com.company.platform.customer.web.ReceivableSummaryView;
import com.company.platform.customer.web.ReceivableView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 财务应收账款服务。
 *
 * <p>职责：应收分页、手工补录、详情、账龄/逾期汇总和客户信用读取口径。
 * 应收的正式来源是发货/开票，当前发货模块未上线，先允许财务手工补录并
 * 标记 {@code MANUAL}，后续接入自动生成时不需要改表结构。</p>
 *
 * <p>数据范围：管理员、财务可看全部；其他角色（如授予了应收查看权限的销售）
 * 只能看当前客户归属为自己的应收。权限码控制“能不能进页面”，数据范围在
 * 服务层再次校验，不能只依赖前端菜单。</p>
 *
 * <p>金额单位统一为分；逾期和账龄实时计算，不落库。应收状态只保存
 * UNPAID/PARTIAL/PAID/CANCELLED，逾期不是状态，避免定时任务失效导致
 * 逾期客户被漏拦。</p>
 */
@Service
public class ReceivableService {

    /** 允许的应收状态。 */
    private static final Set<String> STATUSES = Set.of("UNPAID", "PARTIAL", "PAID", "CANCELLED");
    /** 可被手工应收引用的合同状态；订单也使用同一口径。 */
    private static final Set<String> CONTRACT_USABLE_STATUSES = Set.of("ENTERED", "ACTIVE");
    /** 筛选时允许的虚拟状态：OPEN=未结清，OVERDUE=已逾期。 */
    private static final Set<String> VIRTUAL_STATUSES = Set.of("OPEN", "OVERDUE");

    private final BizReceivableMapper receivableMapper;
    private final BizCustomerMapper customerMapper;
    private final BizContractMapper contractMapper;
    private final CustomerService customerService;

    public ReceivableService(BizReceivableMapper receivableMapper,
                             BizCustomerMapper customerMapper,
                             BizContractMapper contractMapper,
                             CustomerService customerService) {
        this.receivableMapper = receivableMapper;
        this.customerMapper = customerMapper;
        this.contractMapper = contractMapper;
        this.customerService = customerService;
    }

    /**
     * 财务手工登记一笔应收。
     *
     * <p>客户必须存在且在当前用户数据范围内；关联合同必须属于同一客户且为
     * “已录入/生效”；订单号只做非负校验，不跨服务查询订单明细。到期日为空
     * 时按客户账期从今天推算，方便账龄统计。</p>
     */
    @Transactional
    public ReceivableView create(ReceivableCreateCommand command) {
        BizCustomer customer = customerService.requireAccessible(command.customerId());
        if (command.amount() == null || command.amount() <= 0) {
            throw new BizException(400, "应收金额必须大于 0");
        }
        if (command.orderNo() != null && command.orderNo() <= 0) {
            throw new BizException(400, "订单号必须大于 0");
        }
        BizContract contract = null;
        if (command.contractId() != null) {
            contract = contractMapper.selectOneById(command.contractId());
            if (contract == null) {
                throw new BizException(404, "合同不存在：" + command.contractId());
            }
            if (!Objects.equals(contract.getCustomerId(), customer.getId())) {
                throw new BizException(400, "合同不属于该客户，不能登记应收");
            }
            if (!CONTRACT_USABLE_STATUSES.contains(contract.getStatus())) {
                throw new BizException(409, "合同不是“已录入/生效”状态，不能登记应收");
            }
        }

        int paymentDays = customer.getPaymentDays() == null ? 0 : Math.max(0, customer.getPaymentDays());
        LocalDate dueDate = command.dueDate() == null ? LocalDate.now().plusDays(paymentDays) : command.dueDate();
        String currency = defaultText(command.currency(), defaultText(customer.getCurrency(), "CNY"))
                .toUpperCase(Locale.ROOT);
        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();

        BizReceivable receivable = new BizReceivable();
        receivable.setCustomerId(customer.getId());
        receivable.setContractId(command.contractId());
        receivable.setOrderNo(command.orderNo());
        receivable.setSourceType("MANUAL");
        receivable.setAmount(command.amount());
        receivable.setCurrency(currency);
        receivable.setReceivedAmount(0L);
        receivable.setDueDate(dueDate);
        receivable.setStatus("UNPAID");
        receivable.setBusinessOwnerId(customer.getOwnerUserId());
        receivable.setRemark(trimToNull(command.remark()));
        receivable.setCreatedBy(userId);
        receivable.setCreatedAt(now);
        receivable.setUpdatedBy(userId);
        receivable.setUpdatedAt(now);
        receivable.setVersion(0);
        receivableMapper.insert(receivable);
        return ReceivableView.from(receivable, customer, contract);
    }

    /**
     * 财务应收分页。
     *
     * <p>筛选条件：关键字（客户名称/编码/应收单号）、客户、状态、到期日区间。
     * 状态支持虚拟值 {@code OPEN}（未结清）和 {@code OVERDUE}（已逾期）。
     * 排序按到期日升序，让最早到期的应收优先暴露。</p>
     */
    @Transactional(readOnly = true)
    public ReceivablePageView page(long pageNumber, long pageSize, String keyword, Long customerId,
                                   String status, LocalDate dueFrom, LocalDate dueTo) {
        FilterSpec spec = buildFilter(keyword, customerId, status, dueFrom, dueTo);
        QueryWrapper query = QueryWrapper.create();
        applyWhere(query, spec);
        query.orderBy("due_date", true);
        Page<BizReceivable> page = receivableMapper.paginate(Page.of(pageNumber, pageSize), query);

        Map<Long, BizCustomer> customers = loadCustomers(page.getRecords());
        Map<Long, BizContract> contracts = loadContracts(page.getRecords());
        List<ReceivableView> records = page.getRecords().stream()
                .map(item -> ReceivableView.from(item, customers.get(item.getCustomerId()),
                        contracts.get(item.getContractId())))
                .toList();

        QueryWrapper summaryQuery = QueryWrapper.create();
        applyWhere(summaryQuery, spec);
        List<BizReceivable> all = receivableMapper.selectListByQuery(summaryQuery);
        return new ReceivablePageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(),
                summarize(all));
    }

    /** 按 ID 查询应收详情，并执行客户数据范围校验。 */
    @Transactional(readOnly = true)
    public ReceivableView get(long id) {
        BizReceivable receivable = requireReceivable(id);
        BizCustomer customer = customerService.requireAccessible(receivable.getCustomerId());
        BizContract contract = receivable.getContractId() == null ? null
                : contractMapper.selectOneById(receivable.getContractId());
        return ReceivableView.from(receivable, customer, contract);
    }

    /**
     * 查询某客户未结清应收，供收款核销页选择。
     *
     * <p>只返回未取消且有未收余额的应收，按到期日升序；最多 200 条，避免
     * 单次核销弹窗拉取过多数据。数据范围仍由客户归属控制。</p>
     */
    @Transactional(readOnly = true)
    public List<ReceivableView> openByCustomer(long customerId) {
        BizCustomer customer = customerService.requireAccessible(customerId);
        List<BizReceivable> list = receivableMapper.selectListByQuery(QueryWrapper.create()
                .where("customer_id = ? AND status <> 'CANCELLED' AND amount > received_amount", customerId)
                .orderBy("due_date", true));
        Map<Long, BizContract> contracts = loadContracts(list);
        return list.stream()
                .limit(200)
                .map(item -> ReceivableView.from(item, customer, contracts.get(item.getContractId())))
                .toList();
    }

    /** 读取应收实体；不存在时抛 404。 */
    @Transactional(readOnly = true)
    public BizReceivable requireReceivable(long id) {
        BizReceivable receivable = receivableMapper.selectOneById(id);
        if (receivable == null) {
            throw new BizException(404, "应收不存在：" + id);
        }
        return receivable;
    }

    /**
     * 构造筛选条件。
     *
     * <p>关键字使用 EXISTS 子查询匹配客户名称/编码，避免为了展示客户名在
     * 应收表冗余存储；应收单号用自增 ID 模糊匹配。非全量数据角色追加客户
     * 归属子查询，客户交接后自动跟随新业务员。</p>
     */
    private FilterSpec buildFilter(String keyword, Long customerId, String status, LocalDate dueFrom, LocalDate dueTo) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(EXISTS (SELECT 1 FROM biz_customer c WHERE c.id = biz_receivable.customer_id "
                    + "AND (c.name LIKE ? OR c.customer_code LIKE ?)) OR CAST(id AS CHAR) LIKE ?)");
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
            if ("OPEN".equals(normalized)) {
                conditions.add("status <> 'CANCELLED' AND amount > received_amount");
            } else if ("OVERDUE".equals(normalized)) {
                conditions.add("status <> 'CANCELLED' AND amount > received_amount "
                        + "AND due_date IS NOT NULL AND due_date < CURDATE()");
            } else if (STATUSES.contains(normalized)) {
                conditions.add("status = ?");
                arguments.add(normalized);
            } else if (!VIRTUAL_STATUSES.contains(normalized)) {
                throw new BizException(400, "非法应收状态：" + status);
            }
        }
        if (dueFrom != null) {
            conditions.add("due_date >= ?");
            arguments.add(dueFrom);
        }
        if (dueTo != null) {
            conditions.add("due_date <= ?");
            arguments.add(dueTo);
        }
        if (!customerService.canViewAllData()) {
            conditions.add("customer_id IN (SELECT id FROM biz_customer WHERE owner_user_id = ?)");
            arguments.add(SecurityUtils.userId());
        }
        return new FilterSpec(conditions.isEmpty() ? null : String.join(" AND ", conditions),
                arguments.toArray());
    }

    /** 把筛选条件应用到 QueryWrapper；没有条件时不加 where。 */
    private void applyWhere(QueryWrapper query, FilterSpec spec) {
        if (spec.where() != null) {
            query.where(spec.where(), spec.args());
        }
    }

    /** 汇总筛选结果；已取消应收不进入任何金额指标。 */
    private ReceivableSummaryView summarize(List<BizReceivable> receivables) {
        long total = 0L;
        long received = 0L;
        long outstanding = 0L;
        long overdue = 0L;
        long overdueCount = 0L;
        long openCount = 0L;
        Set<Long> customerIds = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (BizReceivable item : receivables) {
            if ("CANCELLED".equals(item.getStatus())) {
                continue;
            }
            long amount = item.getAmount() == null ? 0L : item.getAmount();
            long paid = item.getReceivedAmount() == null ? 0L : item.getReceivedAmount();
            long open = Math.max(0L, amount - paid);
            total += amount;
            received += paid;
            outstanding += open;
            customerIds.add(item.getCustomerId());
            if (open > 0) {
                openCount++;
                if (item.getDueDate() != null
                        && ChronoUnit.DAYS.between(item.getDueDate(), today) > 0) {
                    overdue += open;
                    overdueCount++;
                }
            }
        }
        return new ReceivableSummaryView(total, received, outstanding, overdue, overdueCount, openCount,
                customerIds.size());
    }

    /** 批量加载客户，避免分页列表逐行查询。 */
    private Map<Long, BizCustomer> loadCustomers(List<BizReceivable> receivables) {
        Set<Long> ids = new HashSet<>();
        for (BizReceivable item : receivables) {
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

    /** 批量加载应收关联合同，合同为空时返回空 Map。 */
    private Map<Long, BizContract> loadContracts(List<BizReceivable> receivables) {
        Set<Long> ids = new HashSet<>();
        for (BizReceivable item : receivables) {
            if (item.getContractId() != null) {
                ids.add(item.getContractId());
            }
        }
        Map<Long, BizContract> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<BizContract> contracts = contractMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
        for (BizContract contract : contracts) {
            result.put(contract.getId(), contract);
        }
        return result;
    }

    /** 生成 IN 子句占位符，数量来自集合大小，不拼接用户输入。 */
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

    /** 默认文本，空值回退。 */
    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    /** 筛选条件与参数。 */
    private record FilterSpec(String where, Object[] args) {
    }
}
