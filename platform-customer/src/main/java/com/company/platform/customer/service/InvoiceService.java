package com.company.platform.customer.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.customer.entity.BizContract;
import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.entity.BizInvoice;
import com.company.platform.customer.mapper.BizContractMapper;
import com.company.platform.customer.mapper.BizCustomerMapper;
import com.company.platform.customer.mapper.BizInvoiceMapper;
import com.company.platform.customer.web.InvoiceCreateCommand;
import com.company.platform.customer.web.InvoicePageView;
import com.company.platform.customer.web.InvoiceSummaryView;
import com.company.platform.customer.web.InvoiceView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 手工开票登记服务。
 *
 * <p>V1.4 明确只做内部开票记录，不对接税控、电子发票平台或总账。发票由财务
 * 按纸面/电子发票手工录入，金额为价税合计；系统只校验业务关系，不替用户
 * 换算价税，避免与真实发票出现分位差异。</p>
 *
 * <p>核心规则：</p>
 * <ul>
 *   <li>发票号码全局唯一，重复登记直接拒绝。</li>
 *   <li>关联合同必须属于同一客户且为“已录入/生效”。</li>
 *   <li>同一合同的有效开票金额不得超过合同金额；作废/红冲发票不计入占用。</li>
 *   <li>状态只能按“已登记 → 已寄出 → 客户签收”推进；作废/红冲需要审批，
 *       审批中心上线前不开放接口，数据库保留原发票关联字段。</li>
 * </ul>
 */
@Service
public class InvoiceService {

    /** 允许的发票类型。 */
    private static final Set<String> TYPES = Set.of("VAT_SPECIAL", "VAT_NORMAL", "ELECTRONIC", "OTHER");
    /** 允许的发票状态。 */
    private static final Set<String> STATUSES = Set.of("REGISTERED", "SENT", "SIGNED", "VOID", "REVERSED");
    /** 可被发票引用的合同状态。 */
    private static final Set<String> CONTRACT_USABLE_STATUSES = Set.of("ENTERED", "ACTIVE");
    /** 不占用合同可开票金额的状态。 */
    private static final Set<String> NON_OCCUPYING_STATUSES = Set.of("VOID", "REVERSED");

    private final BizInvoiceMapper invoiceMapper;
    private final BizCustomerMapper customerMapper;
    private final BizContractMapper contractMapper;
    private final CustomerService customerService;

    public InvoiceService(BizInvoiceMapper invoiceMapper,
                          BizCustomerMapper customerMapper,
                          BizContractMapper contractMapper,
                          CustomerService customerService) {
        this.invoiceMapper = invoiceMapper;
        this.customerMapper = customerMapper;
        this.contractMapper = contractMapper;
        this.customerService = customerService;
    }

    /**
     * 登记一张手工发票。
     *
     * <p>价税合计必须大于 0；税额不能为负且不能超过价税合计；税率范围
     * 0–100。开票日期不能晚于今天。合同可开票额度按“合同金额 - 已登记有效
     * 发票金额”计算，超额必须走审批，本期直接拒绝。</p>
     */
    @Transactional
    public InvoiceView create(InvoiceCreateCommand command) {
        BizCustomer customer = customerService.requireAccessible(command.customerId());
        long amount = command.amount() == null ? 0L : command.amount();
        if (amount <= 0) {
            throw new BizException(400, "开票金额必须大于 0");
        }
        long taxAmount = command.taxAmount() == null ? 0L : command.taxAmount();
        if (taxAmount < 0) {
            throw new BizException(400, "税额不能为负数");
        }
        if (taxAmount > amount) {
            throw new BizException(400, "税额不能超过价税合计");
        }
        BigDecimal taxRate = command.taxRate() == null ? BigDecimal.ZERO : command.taxRate();
        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BizException(400, "税率必须在 0–100 之间");
        }
        LocalDate invoiceDate = command.invoiceDate() == null ? LocalDate.now() : command.invoiceDate();
        if (invoiceDate.isAfter(LocalDate.now())) {
            throw new BizException(400, "开票日期不能晚于今天");
        }
        if (command.orderNo() != null && command.orderNo() <= 0) {
            throw new BizException(400, "订单号必须大于 0");
        }
        String type = defaultText(command.invoiceType(), "VAT_NORMAL").toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new BizException(400, "非法发票类型：" + command.invoiceType());
        }
        String invoiceNumber = trimToNull(command.invoiceNumber());
        if (invoiceNumber == null) {
            throw new BizException(400, "发票号码不能为空");
        }
        long duplicated = invoiceMapper.selectCountByQuery(QueryWrapper.create()
                .where("invoice_number = ?", invoiceNumber));
        if (duplicated > 0) {
            throw new BizException(409, "发票号码已登记：" + invoiceNumber);
        }

        BizContract contract = null;
        if (command.contractId() != null) {
            contract = contractMapper.selectOneById(command.contractId());
            if (contract == null) {
                throw new BizException(404, "合同不存在：" + command.contractId());
            }
            if (!Objects.equals(contract.getCustomerId(), customer.getId())) {
                throw new BizException(400, "合同不属于该客户，不能登记发票");
            }
            if (!CONTRACT_USABLE_STATUSES.contains(contract.getStatus())) {
                throw new BizException(409, "合同不是“已录入/生效”状态，不能登记发票");
            }
            checkContractQuota(contract, amount);
        }

        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();
        BizInvoice invoice = new BizInvoice();
        invoice.setCustomerId(customer.getId());
        invoice.setContractId(command.contractId());
        invoice.setOrderNo(command.orderNo());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setInvoiceType(type);
        invoice.setInvoiceTitle(trimToNull(command.invoiceTitle()));
        invoice.setTaxNo(trimToNull(command.taxNo()));
        invoice.setAmount(amount);
        invoice.setTaxAmount(taxAmount);
        invoice.setTaxRate(taxRate);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setStatus("REGISTERED");
        invoice.setAttachmentUrl(trimToNull(command.attachmentUrl()));
        invoice.setRemark(trimToNull(command.remark()));
        invoice.setCreatedBy(userId);
        invoice.setCreatedAt(now);
        invoice.setUpdatedBy(userId);
        invoice.setUpdatedAt(now);
        invoice.setVersion(0);
        invoiceMapper.insert(invoice);
        return InvoiceView.from(invoice, customer, contract);
    }

    /**
     * 发票分页。
     *
     * <p>关键字匹配客户名称/编码、发票号码、开票抬头和系统单号；按开票日期
     * 倒序。汇总指标按筛选条件统计，作废/红冲发票不计入开票总额和税额。</p>
     */
    @Transactional(readOnly = true)
    public InvoicePageView page(long pageNumber, long pageSize, String keyword, Long customerId,
                                String status, LocalDate dateFrom, LocalDate dateTo) {
        FilterSpec spec = buildFilter(keyword, customerId, status, dateFrom, dateTo);
        QueryWrapper query = QueryWrapper.create();
        applyWhere(query, spec);
        query.orderBy("invoice_date", false);
        query.orderBy("id", false);
        Page<BizInvoice> page = invoiceMapper.paginate(Page.of(pageNumber, pageSize), query);

        Map<Long, BizCustomer> customers = loadCustomers(page.getRecords());
        Map<Long, BizContract> contracts = loadContracts(page.getRecords());
        List<InvoiceView> records = page.getRecords().stream()
                .map(item -> InvoiceView.from(item, customers.get(item.getCustomerId()),
                        contracts.get(item.getContractId())))
                .toList();

        QueryWrapper summaryQuery = QueryWrapper.create();
        applyWhere(summaryQuery, spec);
        List<BizInvoice> all = invoiceMapper.selectListByQuery(summaryQuery);
        return new InvoicePageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(),
                summarize(all));
    }

    /** 发票详情，并执行客户数据范围校验。 */
    @Transactional(readOnly = true)
    public InvoiceView get(long id) {
        BizInvoice invoice = requireInvoice(id);
        BizCustomer customer = customerService.requireAccessible(invoice.getCustomerId());
        BizContract contract = invoice.getContractId() == null ? null
                : contractMapper.selectOneById(invoice.getContractId());
        return InvoiceView.from(invoice, customer, contract);
    }

    /** 标记发票已寄出：仅允许从“已登记”推进。 */
    @Transactional
    public InvoiceView markSent(long id) {
        BizInvoice invoice = requireInvoice(id);
        customerService.requireAccessible(invoice.getCustomerId());
        if (!"REGISTERED".equals(invoice.getStatus())) {
            throw new BizException(409, "只有“已登记”状态的发票可以标记寄出");
        }
        return updateStatus(invoice, "SENT");
    }

    /** 标记客户已签收：仅允许从“已寄出”推进。 */
    @Transactional
    public InvoiceView markSigned(long id) {
        BizInvoice invoice = requireInvoice(id);
        customerService.requireAccessible(invoice.getCustomerId());
        if (!"SENT".equals(invoice.getStatus())) {
            throw new BizException(409, "只有“已寄出”状态的发票可以标记签收");
        }
        return updateStatus(invoice, "SIGNED");
    }

    /** 读取发票实体；不存在时抛 404。 */
    @Transactional(readOnly = true)
    public BizInvoice requireInvoice(long id) {
        BizInvoice invoice = invoiceMapper.selectOneById(id);
        if (invoice == null) {
            throw new BizException(404, "发票不存在：" + id);
        }
        return invoice;
    }

    /**
     * 校验合同剩余可开票额度。
     *
     * <p>已作废/已红冲的发票不占用额度；红冲发票如为负数金额，后续接入审批时
     * 需要单独处理，本期不开放红冲登记。</p>
     */
    private void checkContractQuota(BizContract contract, long newAmount) {
        if (contract.getContractAmount() == null) {
            return;
        }
        List<BizInvoice> existing = invoiceMapper.selectListByQuery(QueryWrapper.create()
                .where("contract_id = ?", contract.getId()));
        long occupied = 0L;
        for (BizInvoice item : existing) {
            if (NON_OCCUPYING_STATUSES.contains(item.getStatus())) {
                continue;
            }
            occupied += item.getAmount() == null ? 0L : item.getAmount();
        }
        long quota = contract.getContractAmount();
        long remaining = Math.max(0L, quota - occupied);
        if (newAmount > remaining) {
            throw new BizException(409, "开票金额超过合同剩余可开票额度，剩余 "
                    + remaining + " 分（合同金额 " + quota + " 分）");
        }
    }

    /** 更新状态并返回详情。 */
    private InvoiceView updateStatus(BizInvoice invoice, String status) {
        invoice.setStatus(status);
        invoice.setUpdatedBy(SecurityUtils.userId());
        invoice.setUpdatedAt(LocalDateTime.now());
        invoiceMapper.update(invoice);
        return get(invoice.getId());
    }

    /** 构造筛选条件；非全量数据角色按客户归属过滤。 */
    private FilterSpec buildFilter(String keyword, Long customerId, String status,
                                   LocalDate dateFrom, LocalDate dateTo) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(EXISTS (SELECT 1 FROM biz_customer c WHERE c.id = biz_invoice.customer_id "
                    + "AND (c.name LIKE ? OR c.customer_code LIKE ?)) "
                    + "OR invoice_number LIKE ? OR invoice_title LIKE ? OR CAST(id AS CHAR) LIKE ? "
                    + "OR CONCAT('FP-', LPAD(id, 8, '0')) LIKE ?)");
            arguments.add(like);
            arguments.add(like);
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
                throw new BizException(400, "非法发票状态：" + status);
            }
            conditions.add("status = ?");
            arguments.add(normalized);
        }
        if (dateFrom != null) {
            conditions.add("invoice_date >= ?");
            arguments.add(dateFrom);
        }
        if (dateTo != null) {
            conditions.add("invoice_date <= ?");
            arguments.add(dateTo);
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

    /** 汇总发票金额；作废/红冲不进入金额指标，但仍计入张数。 */
    private InvoiceSummaryView summarize(List<BizInvoice> invoices) {
        long total = 0L;
        long tax = 0L;
        long count = invoices.size();
        long validCount = 0L;
        for (BizInvoice item : invoices) {
            if (NON_OCCUPYING_STATUSES.contains(item.getStatus())) {
                continue;
            }
            total += item.getAmount() == null ? 0L : item.getAmount();
            tax += item.getTaxAmount() == null ? 0L : item.getTaxAmount();
            validCount++;
        }
        return new InvoiceSummaryView(total, tax, count, validCount);
    }

    /** 批量加载客户。 */
    private Map<Long, BizCustomer> loadCustomers(List<BizInvoice> invoices) {
        Set<Long> ids = new HashSet<>();
        for (BizInvoice item : invoices) {
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

    /** 批量加载合同。 */
    private Map<Long, BizContract> loadContracts(List<BizInvoice> invoices) {
        Set<Long> ids = new HashSet<>();
        for (BizInvoice item : invoices) {
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

    /** 生成 IN 占位符，数量来自集合大小，不拼接用户输入。 */
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
