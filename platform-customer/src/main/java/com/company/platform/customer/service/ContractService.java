package com.company.platform.customer.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.customer.entity.BizContract;
import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.mapper.BizContractMapper;
import com.company.platform.customer.web.ContractCreateCommand;
import com.company.platform.customer.web.ContractOrderContext;
import com.company.platform.customer.web.ContractView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 客户合同录入与查询服务。
 *
 * <p>V1.4 要求销售先与客户确定合同，财务正式录入并编号，订单只能引用
 * “已录入/生效”的合同。新建合同默认是“待财务录入”，必须显式调用录入接口，
 * 避免销售草稿被订单误用。</p>
 *
 * <p>合同金额单位为分；合同剩余额度不落库，订单服务按合同汇总未关闭订单
 * 计算，保证订单状态变化后额度立即一致。</p>
 */
@Service
public class ContractService {

    /** 合同类型。 */
    private static final Set<String> CONTRACT_TYPES = Set.of("PROCESSING", "FRAMEWORK", "ANNUAL_PRICE");
    /** 可被订单引用的合同状态。 */
    private static final Set<String> ORDERABLE_STATUSES = Set.of("ENTERED", "ACTIVE");

    private final BizContractMapper contractMapper;
    private final CustomerService customerService;

    public ContractService(BizContractMapper contractMapper, CustomerService customerService) {
        this.contractMapper = contractMapper;
        this.customerService = customerService;
    }

    /**
     * 财务新建合同，初始状态为待录入。
     *
     * <p>合同编号必须唯一；客户必须存在。金额必须大于 0，日期区间不能倒置。</p>
     */
    @Transactional
    public ContractView create(ContractCreateCommand command) {
        BizCustomer customer = customerService.requireAccessible(command.customerId());
        String contractNo = command.contractNo().trim();
        if (contractMapper.selectCountByQuery(
                QueryWrapper.create().where("contract_no = ?", contractNo)) > 0) {
            throw new BizException(409, "合同编号已存在：" + contractNo);
        }
        assertDateRange(command.startDate(), command.endDate());

        BizContract contract = new BizContract();
        contract.setContractNo(contractNo);
        contract.setCustomerId(customer.getId());
        contract.setContractType(normalizeContractType(command.contractType()));
        contract.setStartDate(command.startDate());
        contract.setEndDate(command.endDate());
        contract.setContractAmount(command.contractAmount());
        contract.setCurrency(defaultText(command.currency(), "CNY"));
        contract.setSettlementMethod(defaultText(command.settlementMethod(), customer.getSettlementMethod()));
        contract.setPaymentDays(command.paymentDays() == null ? customer.getPaymentDays() : command.paymentDays());
        contract.setTaxRate(command.taxRate() == null ? customer.getDefaultTaxRate() : command.taxRate());
        contract.setPriceTerms(trimToNull(command.priceTerms()));
        contract.setQualityTerms(trimToNull(command.qualityTerms()));
        contract.setStatus("PENDING_ENTRY");
        contract.setAttachmentUrl(trimToNull(command.attachmentUrl()));
        contract.setRemark(trimToNull(command.remark()));
        contract.setCreatedBy(SecurityUtils.userId());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedBy(contract.getCreatedBy());
        contract.setUpdatedAt(contract.getCreatedAt());
        contract.setVersion(0);

        contractMapper.insert(contract);
        return ContractView.from(contract);
    }

    /**
     * 财务正式录入合同。
     *
     * <p>幂等：已录入/已生效时直接返回当前合同；其他状态（如终止）拒绝操作。
     * 录入人/录入时间从 JWT 取，不能由前端伪造。</p>
     */
    @Transactional
    public ContractView enter(long id) {
        BizContract contract = requireContract(id);
        if (ORDERABLE_STATUSES.contains(contract.getStatus())) {
            return ContractView.from(contract);
        }
        if (!"PENDING_ENTRY".equals(contract.getStatus())) {
            throw new BizException(409, "当前合同状态不允许录入：" + contract.getStatus());
        }
        contract.setStatus("ENTERED");
        contract.setEntryUserId(SecurityUtils.userId());
        contract.setEntryUserName(defaultText(SecurityUtils.nickname(), "财务"));
        contract.setEntryTime(LocalDateTime.now());
        contract.setUpdatedBy(SecurityUtils.userId());
        contract.setUpdatedAt(contract.getEntryTime());
        contractMapper.update(contract);
        return ContractView.from(contract);
    }

    /** 按 ID 查询合同。财务列表和录入后回显使用。 */
    @Transactional(readOnly = true)
    public ContractView get(long id) {
        return ContractView.from(requireContract(id));
    }

    /**
     * 财务合同列表，按合同编号、客户、状态筛选。
     */
    @Transactional(readOnly = true)
    public PageResult<ContractView> page(long pageNumber, long pageSize, String keyword,
                                         Long customerId, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("contract_no LIKE ?");
            arguments.add("%" + keyword.trim() + "%");
        }
        if (customerId != null) {
            conditions.add("customer_id = ?");
            arguments.add(customerId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false);
        Page<BizContract> page = contractMapper.paginate(Page.of(pageNumber, pageSize), query);
        return new PageResult<>(page.getRecords().stream().map(ContractView::from).toList(),
                page.getTotalRow(), page.getPageNumber(), page.getPageSize());
    }

    /**
     * 查询某客户当前可用于下单的合同。
     *
     * <p>只返回已录入/生效且未到期的合同；数据范围仍按客户归属校验。合同剩余
     * 额度由订单服务校验，这里不做跨分片汇总。</p>
     */
    @Transactional(readOnly = true)
    public List<ContractView> available(long customerId) {
        customerService.requireAccessible(customerId);
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ? AND status IN ('ENTERED','ACTIVE')", customerId)
                .and("(end_date IS NULL OR end_date >= ?)", LocalDate.now())
                .orderBy("start_date", false);
        return contractMapper.selectListByQuery(query).stream().map(ContractView::from).toList();
    }

    /**
     * 下单合同上下文。供订单服务在服务端再次校验合同状态和客户归属。
     */
    @Transactional(readOnly = true)
    public ContractOrderContext orderContext(long id) {
        BizContract contract = requireContract(id);
        customerService.requireForOrder(contract.getCustomerId());
        return new ContractOrderContext(contract.getId(), contract.getContractNo(), contract.getCustomerId(),
                contract.getStatus(), contract.getContractAmount(), contract.getCurrency(),
                contract.getStartDate(), contract.getEndDate());
    }

    /** 按 ID 获取合同，不存在时返回 404。 */
    private BizContract requireContract(long id) {
        BizContract contract = contractMapper.selectOneById(id);
        if (contract == null) {
            throw new BizException(404, "合同不存在：" + id);
        }
        return contract;
    }

    /** 校验合同日期区间。 */
    private void assertDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(400, "合同结束日期不能早于开始日期");
        }
    }

    /** 合同类型为空时默认加工合同，非法值拒绝。 */
    private String normalizeContractType(String value) {
        String normalized = value == null || value.isBlank()
                ? "PROCESSING" : value.trim().toUpperCase(Locale.ROOT);
        if (!CONTRACT_TYPES.contains(normalized)) {
            throw new BizException(400, "非法合同类型：" + value);
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

    /** 默认文本。 */
    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}
