package com.company.platform.bi.etl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 从业务库抽取 BI 快照。
 *
 * <p>只执行 SELECT，不修改业务表。订单物理表按两个分片库、各 4 张表广播查询；
 * 设备、排产、仓储只存在 {@code company_order0}；客户、财务、审批只在
 * {@code company_platform}。抽取完成后在内存中补齐跨库关系（订单工厂、客户归属、
 * 信用占用），避免在 SQL 中跨实例连接。</p>
 *
 * <p>所有金额仍以分保存；比例和展示格式留给看板服务。库存金额按 V1.4 要求只取
 * 最近一次采购入库单价，没有入库记录时按 0，不使用物料档案价或估算价。</p>
 */
@Component
public class BiSnapshotExtractor {

    private static final String ORDER_TABLES = "t_order_0,t_order_1,t_order_2,t_order_3";
    private static final String ORDER_COLUMNS = "order_no, customer_id, factory_id, order_status, "
            + "total_amount, created_by, created_at, updated_at";

    private final JdbcTemplate platformJdbc;
    private final JdbcTemplate order0Jdbc;
    private final JdbcTemplate order1Jdbc;

    public BiSnapshotExtractor(@Qualifier("platformJdbc") JdbcTemplate platformJdbc,
                               @Qualifier("order0Jdbc") JdbcTemplate order0Jdbc,
                               @Qualifier("order1Jdbc") JdbcTemplate order1Jdbc) {
        this.platformJdbc = platformJdbc;
        this.order0Jdbc = order0Jdbc;
        this.order1Jdbc = order1Jdbc;
    }

    /** 抽取当前完整快照。 */
    public BiSnapshot extract() {
        LocalDateTime now = LocalDateTime.now();
        List<BiSnapshot.FactoryRow> factories = extractFactories(now);
        List<BiSnapshot.CustomerRow> customers = extractCustomers(now);
        List<BiSnapshot.OrderRow> orders = extractOrders();

        Map<Long, Long> orderFactory = orders.stream()
                .filter(row -> row.orderNo() != null && row.factoryId() != null)
                .collect(Collectors.toMap(BiSnapshot.OrderRow::orderNo, BiSnapshot.OrderRow::factoryId,
                        (left, right) -> left));
        Map<Long, Long> orderOwner = orders.stream()
                .filter(row -> row.orderNo() != null)
                .collect(Collectors.toMap(BiSnapshot.OrderRow::orderNo, BiSnapshot.OrderRow::ownerUserId,
                        (left, right) -> left, HashMap::new));
        Map<Long, BiSnapshot.CustomerRow> customerById = customers.stream()
                .collect(Collectors.toMap(BiSnapshot.CustomerRow::customerId, Function.identity(),
                        (left, right) -> left));

        List<BiSnapshot.ReceivableRow> receivables = extractReceivables(now, orderFactory, customerById);
        List<BiSnapshot.ReceiptRow> receipts = extractReceipts(customerById);
        List<BiSnapshot.ExpenseRow> expenses = extractExpenses();
        List<BiSnapshot.LoanRow> loans = extractLoans();
        List<BiSnapshot.WorkOrderRow> workOrders = extractWorkOrders(orderOwner);
        List<BiSnapshot.EquipmentRow> equipment = extractEquipment();
        List<BiSnapshot.QueueRow> queue = extractQueue();
        List<BiSnapshot.InventoryRow> inventory = extractInventory(now);
        List<BiSnapshot.TransferRow> transfers = extractTransfers();
        List<BiSnapshot.ApprovalRow> approvals = extractApprovals();
        List<BiSnapshot.CreditRow> credits = buildCredits(now, customers, receivables, orders);

        return new BiSnapshot(factories, customers, orders, receivables, receipts, expenses, loans,
                workOrders, equipment, queue, inventory, transfers, approvals, credits);
    }

    /** 工厂维度。 */
    private List<BiSnapshot.FactoryRow> extractFactories(LocalDateTime now) {
        return platformJdbc.query("SELECT id, code, name, status FROM sys_factory ORDER BY id",
                (rs, rowNum) -> new BiSnapshot.FactoryRow(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("status"),
                        now));
    }

    /** 客户维度；归属业务员和常用工厂用于数据范围与应收工厂回退。 */
    private List<BiSnapshot.CustomerRow> extractCustomers(LocalDateTime now) {
        return platformJdbc.query("SELECT id, name, owner_user_id, owner_name, default_factory_id, "
                        + "status, credit_limit, created_at FROM biz_customer ORDER BY id",
                (rs, rowNum) -> new BiSnapshot.CustomerRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        nullableLong(rs, "owner_user_id"),
                        rs.getString("owner_name"),
                        nullableLong(rs, "default_factory_id"),
                        rs.getString("status"),
                        longValue(rs, "credit_limit"),
                        localDateTime(rs, "created_at"),
                        now));
    }

    /** 两个分片库的 8 张订单物理表。 */
    private List<BiSnapshot.OrderRow> extractOrders() {
        List<BiSnapshot.OrderRow> rows = new ArrayList<>();
        rows.addAll(order0Jdbc.query(orderQuery("company_order0"), orderMapper()));
        rows.addAll(order1Jdbc.query(orderQuery("company_order1"), orderMapper()));
        return rows;
    }

    private String orderQuery(String schema) {
        StringBuilder sql = new StringBuilder("SELECT ").append(ORDER_COLUMNS)
                .append(", CASE WHEN order_status = 'COMPLETED' THEN updated_at ELSE NULL END AS completed_at")
                .append(" FROM (");
        String[] tables = ORDER_TABLES.split(",");
        for (int i = 0; i < tables.length; i++) {
            if (i > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT ").append(ORDER_COLUMNS).append(" FROM ")
                    .append(schema).append('.').append(tables[i]);
        }
        sql.append(") x");
        return sql.toString();
    }

    private RowMapper<BiSnapshot.OrderRow> orderMapper() {
        return (rs, rowNum) -> new BiSnapshot.OrderRow(
                rs.getLong("order_no"),
                rs.getLong("customer_id"),
                rs.getLong("factory_id"),
                nullableLong(rs, "created_by"),
                rs.getString("order_status"),
                longValue(rs, "total_amount"),
                localDateTime(rs, "created_at"),
                localDateTime(rs, "completed_at"),
                localDateTime(rs, "updated_at"));
    }

    /**
     * 应收事实。工厂优先取关联订单工厂，没有订单时回退客户常用工厂；业务员优先取
     * 应收自身字段，历史数据为空时回退客户归属业务员。
     */
    private List<BiSnapshot.ReceivableRow> extractReceivables(LocalDateTime now,
                                                               Map<Long, Long> orderFactory,
                                                               Map<Long, BiSnapshot.CustomerRow> customerById) {
        LocalDate today = now.toLocalDate();
        return platformJdbc.query("SELECT id, customer_id, order_no, amount, received_amount, due_date, "
                        + "status, business_owner_id, created_at FROM biz_receivable ORDER BY id",
                (rs, rowNum) -> {
                    long amount = longValue(rs, "amount");
                    long received = longValue(rs, "received_amount");
                    long outstanding = Math.max(0L, amount - received);
                    LocalDate dueDate = localDate(rs, "due_date");
                    String status = rs.getString("status");
                    boolean overdue = !"CANCELLED".equals(status) && outstanding > 0
                            && dueDate != null && dueDate.isBefore(today);
                    int overdueDays = overdue ? (int) Math.min(Integer.MAX_VALUE,
                            ChronoUnit.DAYS.between(dueDate, today)) : 0;
                    Long customerId = rs.getLong("customer_id");
                    Long orderNo = nullableLong(rs, "order_no");
                    BiSnapshot.CustomerRow customer = customerById.get(customerId);
                    Long factoryId = orderNo == null ? null : orderFactory.get(orderNo);
                    if (factoryId == null && customer != null) {
                        factoryId = customer.defaultFactoryId();
                    }
                    Long ownerUserId = nullableLong(rs, "business_owner_id");
                    if (ownerUserId == null && customer != null) {
                        ownerUserId = customer.ownerUserId();
                    }
                    return new BiSnapshot.ReceivableRow(
                            rs.getLong("id"),
                            customerId,
                            orderNo,
                            factoryId,
                            ownerUserId,
                            amount,
                            received,
                            outstanding,
                            dueDate,
                            status,
                            overdue,
                            overdueDays,
                            localDateTime(rs, "created_at"));
                });
    }

    /** 收款事实；收款单本身没有工厂字段，按客户常用工厂提供厂长范围回退。 */
    private List<BiSnapshot.ReceiptRow> extractReceipts(Map<Long, BiSnapshot.CustomerRow> customerById) {
        return platformJdbc.query("SELECT id, customer_id, amount, allocated_amount, received_date, "
                        + "status, created_at FROM biz_receipt ORDER BY id",
                (rs, rowNum) -> {
                    Long customerId = rs.getLong("customer_id");
                    BiSnapshot.CustomerRow customer = customerById.get(customerId);
                    return new BiSnapshot.ReceiptRow(
                            rs.getLong("id"),
                            customerId,
                            customer == null ? null : customer.defaultFactoryId(),
                            customer == null ? null : customer.ownerUserId(),
                            longValue(rs, "amount"),
                            longValue(rs, "allocated_amount"),
                            localDate(rs, "received_date"),
                            rs.getString("status"),
                            localDateTime(rs, "created_at"));
                });
    }

    private List<BiSnapshot.ExpenseRow> extractExpenses() {
        return platformJdbc.query("SELECT id, claimant_user_id, factory_id, amount, expense_date, status, "
                        + "paid_amount, created_at FROM biz_expense_claim ORDER BY id",
                (rs, rowNum) -> new BiSnapshot.ExpenseRow(
                        rs.getLong("id"),
                        rs.getLong("claimant_user_id"),
                        nullableLong(rs, "factory_id"),
                        longValue(rs, "amount"),
                        localDate(rs, "expense_date"),
                        rs.getString("status"),
                        nullableLong(rs, "paid_amount"),
                        localDateTime(rs, "created_at")));
    }

    private List<BiSnapshot.LoanRow> extractLoans() {
        return platformJdbc.query("SELECT id, borrower_user_id, factory_id, amount, loan_date, status, "
                        + "outstanding_amount, created_at FROM biz_loan ORDER BY id",
                (rs, rowNum) -> new BiSnapshot.LoanRow(
                        rs.getLong("id"),
                        rs.getLong("borrower_user_id"),
                        nullableLong(rs, "factory_id"),
                        longValue(rs, "amount"),
                        localDate(rs, "loan_date"),
                        rs.getString("status"),
                        longValue(rs, "outstanding_amount"),
                        localDateTime(rs, "created_at")));
    }

    /** 工单事实；sales_user_id 为空时用订单创建人补齐，保持销售数据范围。 */
    private List<BiSnapshot.WorkOrderRow> extractWorkOrders(Map<Long, Long> orderOwner) {
        return order0Jdbc.query("SELECT id, order_no, customer_id, factory_id, sales_user_id, device_id, "
                        + "status, planned_meters, actual_start, actual_end, created_at FROM t_work_order ORDER BY id",
                (rs, rowNum) -> {
                    Long orderNo = rs.getLong("order_no");
                    Long ownerUserId = nullableLong(rs, "sales_user_id");
                    if (ownerUserId == null) {
                        ownerUserId = orderOwner.get(orderNo);
                    }
                    return new BiSnapshot.WorkOrderRow(
                            rs.getLong("id"),
                            orderNo,
                            rs.getLong("customer_id"),
                            rs.getLong("factory_id"),
                            ownerUserId,
                            nullableLong(rs, "device_id"),
                            rs.getString("status"),
                            decimal(rs, "planned_meters"),
                            localDateTime(rs, "actual_start"),
                            localDateTime(rs, "actual_end"),
                            localDateTime(rs, "created_at"));
                });
    }

    private List<BiSnapshot.EquipmentRow> extractEquipment() {
        return order0Jdbc.query("SELECT id, factory_id, device_type, status, capacity_meters_per_hour, "
                        + "updated_at FROM t_device ORDER BY id",
                (rs, rowNum) -> new BiSnapshot.EquipmentRow(
                        rs.getLong("id"),
                        rs.getLong("factory_id"),
                        rs.getString("device_type"),
                        rs.getString("status"),
                        decimal(rs, "capacity_meters_per_hour"),
                        localDateTime(rs, "updated_at")));
    }

    private List<BiSnapshot.QueueRow> extractQueue() {
        return order0Jdbc.query("SELECT q.id, q.device_id, q.work_order_id, w.factory_id, q.status, "
                        + "q.enqueued_at, q.planned_start, w.actual_start "
                        + "FROM t_device_queue q LEFT JOIN t_work_order w ON w.id = q.work_order_id ORDER BY q.id",
                (rs, rowNum) -> new BiSnapshot.QueueRow(
                        rs.getLong("id"),
                        rs.getLong("device_id"),
                        rs.getLong("work_order_id"),
                        rs.getLong("factory_id"),
                        rs.getString("status"),
                        localDateTime(rs, "enqueued_at"),
                        localDateTime(rs, "planned_start"),
                        localDateTime(rs, "actual_start")));
    }

    /**
     * 库存快照。单价只从已入库的采购入库明细取最近一条，保证材料成本来源符合 V1.4。
     * 数量 × 单价 × 100 后直接截断到分，不使用四舍五入。
     */
    private List<BiSnapshot.InventoryRow> extractInventory(LocalDateTime now) {
        return order0Jdbc.query("SELECT i.id, i.warehouse_id, i.factory_id, i.material_id, m.material_name, "
                        + "i.quantity, m.safety_stock, i.updated_at, "
                        + "COALESCE((SELECT l.unit_price FROM t_purchase_receipt_line l "
                        + "JOIN t_purchase_receipt r ON r.id = l.receipt_id "
                        + "WHERE l.material_id = i.material_id AND r.status = 'RECEIVED' "
                        + "ORDER BY r.receipt_date DESC, l.id DESC LIMIT 1), 0) AS unit_cost "
                        + "FROM t_inventory i JOIN t_material m ON m.id = i.material_id ORDER BY i.id",
                (rs, rowNum) -> {
                    BigDecimal quantity = decimal(rs, "quantity");
                    BigDecimal unitCost = decimal(rs, "unit_cost");
                    long amount = quantity.multiply(unitCost).multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.DOWN).longValue();
                    return new BiSnapshot.InventoryRow(
                            rs.getLong("id"),
                            rs.getLong("warehouse_id"),
                            rs.getLong("factory_id"),
                            rs.getLong("material_id"),
                            rs.getString("material_name"),
                            quantity,
                            unitCost,
                            amount,
                            decimal(rs, "safety_stock"),
                            localDateTimeOrNow(rs, "updated_at", now));
                });
    }

    private List<BiSnapshot.TransferRow> extractTransfers() {
        return order0Jdbc.query("SELECT id, from_factory_id, to_factory_id, status, shipped_at, received_at, "
                        + "created_at FROM t_stock_transfer ORDER BY id",
                (rs, rowNum) -> new BiSnapshot.TransferRow(
                        rs.getLong("id"),
                        rs.getLong("from_factory_id"),
                        rs.getLong("to_factory_id"),
                        rs.getString("status"),
                        localDateTime(rs, "shipped_at"),
                        localDateTime(rs, "received_at"),
                        localDateTime(rs, "created_at")));
    }

    private List<BiSnapshot.ApprovalRow> extractApprovals() {
        return platformJdbc.query("SELECT id, business_type, business_id, factory_id, applicant_user_id, "
                        + "amount, status, created_at, finished_at, "
                        + "CASE WHEN finished_at IS NULL THEN NULL "
                        + "ELSE TIMESTAMPDIFF(SECOND, created_at, finished_at) END AS duration_seconds "
                        + "FROM approval_instance ORDER BY id",
                (rs, rowNum) -> new BiSnapshot.ApprovalRow(
                        rs.getLong("id"),
                        rs.getString("business_type"),
                        rs.getString("business_id"),
                        nullableLong(rs, "factory_id"),
                        rs.getLong("applicant_user_id"),
                        nullableLong(rs, "amount"),
                        rs.getString("status"),
                        localDateTime(rs, "created_at"),
                        localDateTime(rs, "finished_at"),
                        nullableLong(rs, "duration_seconds")));
    }

    /**
     * 信用占用：未收应收 + 未关闭订单。与信用看板/下单校验保持同一口径。
     * 信用额度为 0 表示未授信，不算超限。
     */
    private List<BiSnapshot.CreditRow> buildCredits(LocalDateTime now,
                                                    List<BiSnapshot.CustomerRow> customers,
                                                    List<BiSnapshot.ReceivableRow> receivables,
                                                    List<BiSnapshot.OrderRow> orders) {
        Map<Long, Long> receivableOpen = receivables.stream()
                .filter(row -> !"CANCELLED".equals(row.status()))
                .collect(Collectors.groupingBy(BiSnapshot.ReceivableRow::customerId,
                        Collectors.summingLong(BiSnapshot.ReceivableRow::outstandingAmount)));
        Map<Long, Long> orderOpen = orders.stream()
                .filter(row -> !"CANCELLED".equals(row.orderStatus())
                        && !"COMPLETED".equals(row.orderStatus()))
                .collect(Collectors.groupingBy(BiSnapshot.OrderRow::customerId,
                        Collectors.summingLong(BiSnapshot.OrderRow::totalAmount)));

        List<BiSnapshot.CreditRow> result = new ArrayList<>();
        for (BiSnapshot.CustomerRow customer : customers) {
            long recv = receivableOpen.getOrDefault(customer.customerId(), 0L);
            long open = orderOpen.getOrDefault(customer.customerId(), 0L);
            long used = recv + open;
            long limit = customer.creditLimit() == null ? 0L : customer.creditLimit();
            boolean over = limit > 0 && used > limit;
            result.add(new BiSnapshot.CreditRow(customer.customerId(), customer.customerName(),
                    customer.ownerUserId(), limit, recv, open, used, over, now));
        }
        return result;
    }

    /** JDBC 的 getLong 会把 NULL 变成 0，这里保留 NULL 语义。 */
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** 金额字段非空时使用 getLong；数据库 NULL 按 0 处理。 */
    private static long longValue(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? 0L : value;
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static LocalDateTime localDateTimeOrNow(ResultSet rs, String column, LocalDateTime now)
            throws SQLException {
        LocalDateTime value = localDateTime(rs, column);
        return value == null ? now : value;
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
