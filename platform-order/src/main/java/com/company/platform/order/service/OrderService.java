package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.core.money.MoneyUtils;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.client.ContractOrderContext;
import com.company.platform.order.client.CustomerOrderContext;
import com.company.platform.order.client.CustomerServiceClient;
import com.company.platform.order.entity.BizOrder;
import com.company.platform.order.mapper.BizOrderMapper;
import com.company.platform.order.web.CreateOrderCommand;
import com.company.platform.order.web.CreditOccupancyView;
import com.company.platform.order.web.OrderView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单领域服务。
 *
 * <p>负责订单主数据写入和查询，以及下单前置的客户状态、合同、信用和逾期
 * 校验。设备排产、工单拆分和队列规则将在设备/生产模块接入。</p>
 *
 * <p><strong>金额规则：</strong>{@code totalAmount} 始终是整数分，调用方不得传入
 * 浮点元；任何计算都必须直接截断到分，禁止四舍五入。</p>
 */
@Service
public class OrderService {

    /** 合同允许下单的状态；与客户服务保持业务一致。 */
    private static final java.util.Set<String> ORDERABLE_CONTRACT_STATUSES =
            java.util.Set.of("ENTERED", "ACTIVE");

    private final BizOrderMapper orderMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final CustomerServiceClient customerServiceClient;

    /**
     * @param orderMapper          订单 Mapper
     * @param customerServiceClient 客户/合同校验客户端
     * @param workerId             雪花算法机器号，多实例部署时必须唯一
     * @param datacenterId         雪花算法数据中心号
     */
    public OrderService(BizOrderMapper orderMapper,
                        CustomerServiceClient customerServiceClient,
                        @Value("${app.snowflake.worker-id:1}") long workerId,
                        @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.orderMapper = orderMapper;
        this.customerServiceClient = customerServiceClient;
        this.idGenerator = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    /**
     * 创建订单。
     *
     * <p>订单号在应用层生成，不使用数据库自增：雪花号全局唯一、趋势递增，
     * 同时作为分片键参与库表路由。初始状态固定为 CREATED，币种为空时按 CNY。</p>
     *
     * <p>创建前通过客户服务校验：客户存在且为合作中、合同已录入、合同属于
     * 该客户、合同未过期、合同剩余额度足够、信用额度未超、应收逾期未超过
     * 30 天。客户服务不可用时失败关闭，避免绕过风控。</p>
     *
     * <p><strong>并发说明：</strong>额度校验与订单落库不在同一数据库事务内，
     * 多实例并发下单可能同时通过校验。生产环境应增加客户/合同维度的分布式
     * 锁或额度预占表；当前单体开发环境先保证串行场景正确。</p>
     *
     * @param command 前端提交的订单字段，金额单位为分
     * @param userId  当前登录用户，来自 JWT 的 user_id
     * @return 已落库的订单视图
     */
    @Transactional
    public OrderView create(CreateOrderCommand command, Long userId) {
        CustomerOrderContext customer = customerServiceClient.customerContext(command.customerId());
        ContractOrderContext contract = customerServiceClient.contractContext(command.contractId());
        String currency = command.currency() == null || command.currency().isBlank()
                ? "CNY" : command.currency().trim().toUpperCase(java.util.Locale.ROOT);
        validateContract(command, contract, currency);
        validateCredit(command, customer);

        LocalDateTime now = LocalDateTime.now();
        BizOrder order = new BizOrder();
        order.setOrderNo(idGenerator.nextId());
        order.setCustomerId(command.customerId());
        order.setContractId(command.contractId());
        order.setFactoryId(command.factoryId());
        order.setOrderStatus("CREATED");
        order.setTotalAmount(command.totalAmount());
        order.setCurrency(currency);
        order.setRemark(command.remark());
        order.setCreatedBy(userId);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
        return OrderView.from(order);
    }

    /**
     * 合同校验：状态、客户归属、有效期、币种和合同剩余额度。
     *
     * <p>合同已下单金额按同一合同汇总未关闭订单。发货、开票、收款尚未实现，
     * 当前“剩余额度”只扣除未完成订单，后续需要扩展为合同执行视图口径。</p>
     */
    private void validateContract(CreateOrderCommand command, ContractOrderContext contract, String currency) {
        if (contract == null) {
            throw new BizException(409, "合同不存在或已被删除，请重新选择已录入合同");
        }
        if (!java.util.Objects.equals(contract.customerId(), command.customerId())) {
            throw new BizException(409, "所选合同不属于当前客户，不能用于本订单");
        }
        if (!ORDERABLE_CONTRACT_STATUSES.contains(contract.status())) {
            throw new BizException(409, "合同状态不是“已录入/生效”，请由财务正式录入后再下单");
        }
        LocalDate today = LocalDate.now();
        if (contract.startDate() != null && contract.startDate().isAfter(today)) {
            throw new BizException(409, "合同尚未生效，开始日期为 " + contract.startDate());
        }
        if (contract.endDate() != null && contract.endDate().isBefore(today)) {
            throw new BizException(409, "合同已过期，结束日期为 " + contract.endDate());
        }
        if (contract.contractAmount() == null || contract.contractAmount() <= 0) {
            throw new BizException(409, "合同金额无效，请财务核对合同");
        }
        String contractCurrency = contract.currency() == null ? "CNY" : contract.currency().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Objects.equals(currency, contractCurrency)) {
            throw new BizException(409, "订单币种 " + currency + " 与合同币种 " + contractCurrency + " 不一致");
        }
        if (!"CNY".equals(currency)) {
            // 当前合同只维护 CNY；美元等外币合同落地前必须先扩展合同币种校验。
            throw new BizException(409, "当前仅支持人民币合同下单");
        }
        long contractUsed = sumOpenAmountByContract(contract.contractId());
        long remaining = contract.contractAmount() - contractUsed;
        if (remaining < command.totalAmount()) {
            throw new BizException(409, "合同剩余额度不足：剩余 "
                    + MoneyUtils.fromCents(Math.max(0, remaining)).toPlainString()
                    + " 元，本次订单 " + MoneyUtils.fromCents(command.totalAmount()).toPlainString()
                    + " 元，请财务调整合同或走额度审批");
        }
    }

    /**
     * 信用校验：已用 = 未收应收 + 已确认未发货订单；逾期超过 30 天直接阻断。
     *
     * <p>当前订单状态 CREATED/QUEUED/IN_PRODUCTION 都视为未发货占用，取消和
     * 完成订单不占用。信用额度 0 表示未授信，不能赊销下单。</p>
     */
    private void validateCredit(CreateOrderCommand command, CustomerOrderContext customer) {
        if (customer == null) {
            throw new BizException(409, "客户不存在或已被删除");
        }
        int overdueDays = customer.maxOverdueDays() == null ? 0 : customer.maxOverdueDays();
        if (overdueDays > 30) {
            throw new BizException(409, "客户应收已逾期 " + overdueDays + " 天（超过 30 天），暂停下单");
        }
        long limit = customer.creditLimit() == null ? 0L : customer.creditLimit();
        if (limit <= 0) {
            throw new BizException(409, "客户尚未授予信用额度，请财务维护后再下单");
        }
        long receivable = customer.uncollectedReceivable() == null ? 0L : customer.uncollectedReceivable();
        long orderOccupied = sumOpenAmountByCustomer(command.customerId());
        long used = receivable + orderOccupied;
        if (used + command.totalAmount() > limit) {
            throw new BizException(409, "客户信用额度不足：已用 "
                    + MoneyUtils.fromCents(used).toPlainString() + " 元，本次 "
                    + MoneyUtils.fromCents(command.totalAmount()).toPlainString() + " 元，额度 "
                    + MoneyUtils.fromCents(limit).toPlainString() + " 元");
        }
    }

    /**
     * 按订单号查询详情。订单号是分片键，ShardingSphere 会精确路由到唯一物理表，
     * 不会产生跨库广播。
     *
     * @throws BizException 404 当订单不存在
     */
    @Transactional(readOnly = true)
    public OrderView get(long orderNo) {
        BizOrder order = orderMapper.selectOneById(orderNo);
        if (order == null) {
            throw new BizException(404, "Order not found: " + orderNo);
        }
        if (!canAccessOrder(order)) {
            throw new BizException(403, "无权查看其他业务员或其他工厂的订单");
        }
        return OrderView.from(order);
    }

    /**
     * 查询某个客户的全部订单，按创建时间升序。
     *
     * <p>客户编号不是分片键，本次查询会广播到 8 张物理表后合并排序。
     * 数据量增长后应改为分页查询并增加时间范围条件，避免返回过大结果集。</p>
     */
    @Transactional(readOnly = true)
    public List<OrderView> listByCustomer(Long customerId) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId)
                .orderBy("created_at");
        return orderMapper.selectListByQuery(query).stream()
                .filter(this::canAccessOrder)
                .map(OrderView::from)
                .toList();
    }

    /**
     * 分页查询订单。除订单号外，其余条件都会广播到 8 张物理表后由 ShardingSphere 合并。
     * 订单号是分片键，传入时会精确路由到唯一库表，适合详情页和客服查询。
     */
    @Transactional(readOnly = true)
    public PageResult<OrderView> page(long pageNumber, long pageSize, Long orderNo,
                                      Long customerId, Long factoryId, String orderStatus) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (orderNo != null) {
            conditions.add("order_no = ?");
            arguments.add(orderNo);
        }
        if (customerId != null) {
            conditions.add("customer_id = ?");
            arguments.add(customerId);
        }
        if (factoryId != null) {
            conditions.add("factory_id = ?");
            arguments.add(factoryId);
        }
        if (orderStatus != null && !orderStatus.isBlank()) {
            conditions.add("order_status = ?");
            arguments.add(orderStatus.trim());
        }
        appendDataScope(conditions, arguments);

        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false);

        Page<BizOrder> page = orderMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<OrderView> records = page.getRecords().stream().map(OrderView::from).toList();
        return new PageResult<>(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize());
    }

    /**
     * 按当前角色追加工单数据范围。
     *
     * <p>管理员/财务可看全部；厂长只看本厂；其他角色（销售/操作员）只看自己
     * 创建的订单。真正接入业务员归属字段后，销售范围应改为客户归属业务员，
     * 这里先以创建人作为最小可用隔离。</p>
     */
    private void appendDataScope(List<String> conditions, List<Object> arguments) {
        if (isOrgWideScope()) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            Long factoryId = SecurityUtils.factoryId();
            conditions.add("factory_id = ?");
            arguments.add(factoryId == null ? -1L : factoryId);
            return;
        }
        Long userId = SecurityUtils.userId();
        conditions.add("created_by = ?");
        arguments.add(userId == null ? -1L : userId);
    }

    /** 单条订单的数据范围判断，与列表规则保持一致。 */
    private boolean canAccessOrder(BizOrder order) {
        if (isOrgWideScope()) {
            return true;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            return java.util.Objects.equals(order.getFactoryId(), SecurityUtils.factoryId());
        }
        return java.util.Objects.equals(order.getCreatedBy(), SecurityUtils.userId());
    }

    /**
     * 是否按全组织范围处理。
     *
     * <p>管理员/财务可看全部；机器令牌（客户端凭证）代表组织身份，其可访问的
     * 接口完全由 OAuth scope 决定，因此同样按全组织范围处理。</p>
     */
    private boolean isOrgWideScope() {
        return SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE");
    }

    /**
     * 查询某客户的订单信用占用，供财务信用看板与下单前校验复用。
     *
     * <p>占用口径与下单校验一致：未关闭订单（非“已取消/已完成”）全额计入，
     * 不按合同或发货比例折算。客户编号不是分片键，查询会广播到 8 张表；
     * 当前数据量小可以接受，后续应增加按客户的汇总表或异步统计。</p>
     *
     * @param customerId 客户 ID
     * @return 未关闭订单金额与数量，金额单位分
     */
    @Transactional(readOnly = true)
    public CreditOccupancyView creditOccupancy(long customerId) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ? AND order_status NOT IN ('CANCELLED','COMPLETED')", customerId);
        List<BizOrder> orders = orderMapper.selectListByQuery(query);
        return new CreditOccupancyView(customerId, sumAmount(orders), (long) orders.size());
    }

    /**
     * 汇总某客户未关闭订单金额，作为“已确认未发货”信用占用。
     *
     * <p>客户编号不是分片键，ShardingSphere 会广播到 8 张表后求和。当前数据量
     * 小可以接受；后续应在订单表增加按客户的汇总表或异步统计，避免每次下单
     * 都扫描全部分片。</p>
     */
    private long sumOpenAmountByCustomer(long customerId) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ? AND order_status NOT IN ('CANCELLED','COMPLETED')", customerId);
        return sumAmount(orderMapper.selectListByQuery(query));
    }

    /** 汇总某合同未关闭订单金额，用于计算合同剩余额度。 */
    private long sumOpenAmountByContract(long contractId) {
        QueryWrapper query = QueryWrapper.create()
                .where("contract_id = ? AND order_status NOT IN ('CANCELLED','COMPLETED')", contractId);
        return sumAmount(orderMapper.selectListByQuery(query));
    }

    /** 对订单金额做 null 安全的整数求和；金额单位始终为分。 */
    private long sumAmount(List<BizOrder> orders) {
        long total = 0L;
        for (BizOrder order : orders) {
            if (order.getTotalAmount() != null && order.getTotalAmount() > 0) {
                total += order.getTotalAmount();
            }
        }
        return total;
    }
}
