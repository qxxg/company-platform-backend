package com.company.platform.customer.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.entity.BizReceipt;
import com.company.platform.customer.entity.BizReceiptAllocation;
import com.company.platform.customer.entity.BizReceivable;
import com.company.platform.customer.mapper.BizCustomerMapper;
import com.company.platform.customer.mapper.BizReceiptAllocationMapper;
import com.company.platform.customer.mapper.BizReceiptMapper;
import com.company.platform.customer.mapper.BizReceivableMapper;
import com.company.platform.customer.web.ReceiptAllocateCommand;
import com.company.platform.customer.web.ReceiptAllocationView;
import com.company.platform.customer.web.ReceiptCreateCommand;
import com.company.platform.customer.web.ReceiptView;
import com.company.platform.customer.web.ReceivableView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 收款登记与核销服务。
 *
 * <p>核心规则：</p>
 * <ul>
 *   <li>收款单只记录实际到账，不直接修改应收；核销后才减少应收余额。</li>
 *   <li>一张收款单可核销多张应收，一张应收也可分多次收款。</li>
 *   <li>核销总额不能超过收款单余额，单张应收核销额不能超过其未收余额。</li>
 *   <li>核销在事务内先锁收款单，再按应收 ID 升序加锁，防止并发超收和死锁。</li>
 *   <li>核销明细只追加，不修改、不物理删除；作废/红冲后续走审批。</li>
 * </ul>
 *
 * <p>金额单位统一为分；不允许浮点数和四舍五入。收款方式只做内部记录，
 * 不对接银行、第三方支付或电子回单。</p>
 */
@Service
public class ReceiptService {

    /** 允许的收款方式。 */
    private static final Set<String> METHODS = Set.of("BANK_TRANSFER", "ACCEPTANCE", "CASH", "OTHER");
    /** 允许的收款单状态。 */
    private static final Set<String> STATUSES = Set.of("UNALLOCATED", "PARTIAL", "ALLOCATED", "CANCELLED");

    private final BizReceiptMapper receiptMapper;
    private final BizReceiptAllocationMapper allocationMapper;
    private final BizReceivableMapper receivableMapper;
    private final BizCustomerMapper customerMapper;
    private final CustomerService customerService;

    public ReceiptService(BizReceiptMapper receiptMapper,
                          BizReceiptAllocationMapper allocationMapper,
                          BizReceivableMapper receivableMapper,
                          BizCustomerMapper customerMapper,
                          CustomerService customerService) {
        this.receiptMapper = receiptMapper;
        this.allocationMapper = allocationMapper;
        this.receivableMapper = receivableMapper;
        this.customerMapper = customerMapper;
        this.customerService = customerService;
    }

    /**
     * 登记一笔客户收款。
     *
     * <p>只登记到账，不指定核销对象；未核销余额作为预收/待认领。收款日期
     * 不允许晚于今天，避免把未来资金提前计入应收。</p>
     */
    @Transactional
    public ReceiptView create(ReceiptCreateCommand command) {
        BizCustomer customer = customerService.requireAccessible(command.customerId());
        if (command.amount() == null || command.amount() <= 0) {
            throw new BizException(400, "收款金额必须大于 0");
        }
        LocalDate receivedDate = command.receivedDate() == null ? LocalDate.now() : command.receivedDate();
        if (receivedDate.isAfter(LocalDate.now())) {
            throw new BizException(400, "收款日期不能晚于今天");
        }
        String method = defaultText(command.method(), "BANK_TRANSFER").toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw new BizException(400, "非法收款方式：" + command.method());
        }
        String currency = defaultText(command.currency(), defaultText(customer.getCurrency(), "CNY"))
                .toUpperCase(Locale.ROOT);
        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();

        BizReceipt receipt = new BizReceipt();
        receipt.setCustomerId(customer.getId());
        receipt.setCurrency(currency);
        receipt.setAmount(command.amount());
        receipt.setAllocatedAmount(0L);
        receipt.setReceivedDate(receivedDate);
        receipt.setMethod(method);
        receipt.setBankAccount(trimToNull(command.bankAccount()));
        receipt.setSerialNo(trimToNull(command.serialNo()));
        receipt.setAttachmentUrl(trimToNull(command.attachmentUrl()));
        receipt.setStatus("UNALLOCATED");
        receipt.setRemark(trimToNull(command.remark()));
        receipt.setCreatedBy(userId);
        receipt.setCreatedAt(now);
        receipt.setUpdatedBy(userId);
        receipt.setUpdatedAt(now);
        receipt.setVersion(0);
        receiptMapper.insert(receipt);
        return ReceiptView.from(receipt, customer, List.of());
    }

    /**
     * 收款单分页。
     *
     * <p>按收款日期倒序展示；关键字支持客户名称/编码、流水号和收款单号。
     * 财务/管理员看全部，其他角色按客户归属过滤。</p>
     */
    @Transactional(readOnly = true)
    public PageResult<ReceiptView> page(long pageNumber, long pageSize, String keyword, Long customerId,
                                        String status, LocalDate dateFrom, LocalDate dateTo) {
        FilterSpec spec = buildFilter(keyword, customerId, status, dateFrom, dateTo);
        QueryWrapper query = QueryWrapper.create();
        applyWhere(query, spec);
        query.orderBy("received_date", false);
        query.orderBy("id", false);
        Page<BizReceipt> page = receiptMapper.paginate(Page.of(pageNumber, pageSize), query);
        Map<Long, BizCustomer> customers = loadCustomers(page.getRecords());
        List<ReceiptView> records = page.getRecords().stream()
                .map(item -> ReceiptView.from(item, customers.get(item.getCustomerId()), List.of()))
                .toList();
        return new PageResult<>(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize());
    }

    /** 收款详情，包含核销明细。 */
    @Transactional(readOnly = true)
    public ReceiptView get(long id) {
        BizReceipt receipt = requireReceipt(id);
        BizCustomer customer = customerService.requireAccessible(receipt.getCustomerId());
        return ReceiptView.from(receipt, customer, loadAllocations(receipt.getId()));
    }

    /**
     * 核销收款单。
     *
     * <p>事务步骤：锁定收款单 → 校验余额 → 按应收 ID 升序锁定并校验余额 →
     * 写入核销明细 → 原子累加应收已收金额 → 原子累加收款单已核销金额。
     * 任一步失败都抛异常回滚，不能留下“钱已分配、应收未更新”的中间状态。</p>
     */
    @Transactional
    public ReceiptView allocate(long receiptId, ReceiptAllocateCommand command) {
        BizReceipt receipt = receiptMapper.selectForUpdate(receiptId);
        if (receipt == null) {
            throw new BizException(404, "收款单不存在：" + receiptId);
        }
        customerService.requireAccessible(receipt.getCustomerId());
        if ("CANCELLED".equals(receipt.getStatus())) {
            throw new BizException(409, "收款单已作废，不能核销");
        }
        long amount = safe(receipt.getAmount());
        long allocated = safe(receipt.getAllocatedAmount());
        long available = amount - allocated;
        if (available <= 0) {
            throw new BizException(409, "收款单已核销完，没有可用余额");
        }
        if (command == null || command.allocations() == null || command.allocations().isEmpty()) {
            throw new BizException(400, "核销明细不能为空");
        }

        // 先校验并聚合，重复应收直接拒绝，避免同一张应收在一笔核销中出现两次。
        Map<Long, Long> lines = new LinkedHashMap<>();
        long total = 0L;
        for (ReceiptAllocateCommand.AllocationLine line : command.allocations()) {
            if (line == null || line.receivableId() == null || line.amount() == null || line.amount() <= 0) {
                throw new BizException(400, "核销明细中的应收和金额必须有效");
            }
            if (lines.putIfAbsent(line.receivableId(), line.amount()) != null) {
                throw new BizException(400, "同一张应收不能在一笔核销中重复出现");
            }
            total = Math.addExact(total, line.amount());
        }
        if (total > available) {
            throw new BizException(409, "核销总额超过收款单可用余额");
        }

        Long userId = SecurityUtils.userId();
        LocalDateTime now = LocalDateTime.now();
        // 按应收 ID 升序加锁，降低多张收款单交叉核销时的死锁概率。
        List<Long> receivableIds = new ArrayList<>(lines.keySet());
        receivableIds.sort(Long::compareTo);
        for (Long receivableId : receivableIds) {
            long lineAmount = lines.get(receivableId);
            BizReceivable receivable = receivableMapper.selectForUpdate(receivableId);
            if (receivable == null) {
                throw new BizException(404, "应收不存在：" + receivableId);
            }
            if (!Objects.equals(receivable.getCustomerId(), receipt.getCustomerId())) {
                throw new BizException(400, "应收不属于该收款单的客户，不能核销");
            }
            if ("CANCELLED".equals(receivable.getStatus())) {
                throw new BizException(409, "应收已取消，不能核销：" + receivableId);
            }
            long open = safe(receivable.getAmount()) - safe(receivable.getReceivedAmount());
            if (lineAmount > open) {
                throw new BizException(409, "核销金额超过应收未收余额：" + ReceivableView.numberOf(receivableId));
            }

            BizReceiptAllocation allocation = new BizReceiptAllocation();
            allocation.setReceiptId(receiptId);
            allocation.setReceivableId(receivableId);
            allocation.setAmount(lineAmount);
            allocation.setCreatedBy(userId);
            allocation.setCreatedAt(now);
            allocationMapper.insert(allocation);

            if (receivableMapper.addReceivedAmount(receivableId, lineAmount) != 1) {
                throw new BizException(409, "应收余额已变化，请刷新后重试：" + ReceivableView.numberOf(receivableId));
            }
        }
        if (receiptMapper.addAllocatedAmount(receiptId, total) != 1) {
            throw new BizException(409, "收款单余额已变化，请刷新后重试");
        }
        return get(receiptId);
    }

    /** 读取收款单，不存在时抛 404。 */
    @Transactional(readOnly = true)
    public BizReceipt requireReceipt(long id) {
        BizReceipt receipt = receiptMapper.selectOneById(id);
        if (receipt == null) {
            throw new BizException(404, "收款单不存在：" + id);
        }
        return receipt;
    }

    /** 查询一张收款单的核销明细，并批量补齐应收信息。 */
    private List<ReceiptAllocationView> loadAllocations(long receiptId) {
        List<BizReceiptAllocation> allocations = allocationMapper.selectListByQuery(QueryWrapper.create()
                .where("receipt_id = ?", receiptId)
                .orderBy("id", true));
        if (allocations.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        for (BizReceiptAllocation allocation : allocations) {
            ids.add(allocation.getReceivableId());
        }
        Map<Long, BizReceivable> receivables = new HashMap<>();
        List<BizReceivable> list = receivableMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
        for (BizReceivable item : list) {
            receivables.put(item.getId(), item);
        }
        return allocations.stream()
                .map(item -> ReceiptAllocationView.from(item, receivables.get(item.getReceivableId())))
                .toList();
    }

    /** 构造收款筛选条件。 */
    private FilterSpec buildFilter(String keyword, Long customerId, String status,
                                   LocalDate dateFrom, LocalDate dateTo) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(EXISTS (SELECT 1 FROM biz_customer c WHERE c.id = biz_receipt.customer_id "
                    + "AND (c.name LIKE ? OR c.customer_code LIKE ?)) OR serial_no LIKE ? OR CAST(id AS CHAR) LIKE ?)");
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
                throw new BizException(400, "非法收款单状态：" + status);
            }
            conditions.add("status = ?");
            arguments.add(normalized);
        }
        if (dateFrom != null) {
            conditions.add("received_date >= ?");
            arguments.add(dateFrom);
        }
        if (dateTo != null) {
            conditions.add("received_date <= ?");
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

    /** 批量加载客户。 */
    private Map<Long, BizCustomer> loadCustomers(List<BizReceipt> receipts) {
        Set<Long> ids = new HashSet<>();
        for (BizReceipt item : receipts) {
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

    /** 生成 IN 占位符，集合大小决定数量，不拼接用户输入。 */
    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    /** 空金额按 0 处理，避免历史数据 null 导致 NPE。 */
    private long safe(Long value) {
        return value == null ? 0L : value;
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
