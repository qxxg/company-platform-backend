package com.company.platform.bi.service;

import com.company.platform.bi.web.BiDashboardView;
import com.company.platform.bi.etl.BiEtlService;
import com.company.platform.common.security.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 首页工作台与经营大屏聚合服务。
 *
 * <p>只查询 {@code company_bi} 的只读事实表，不直接访问业务库。每个指标都在 SQL
 * 中追加当前用户的数据范围条件；无权限的模块不返回任何数据，而不是返回全公司
 * 汇总后让前端隐藏。金额展示统一由后端按分转元并直接截断，前端不做金额除法。</p>
 *
 * <p>不可计算指标从 {@code bi_metric} 读取并标记“待数据源”，不会用 0 或近似值
 * 填充，避免管理层误读。</p>
 */
@Service
public class BiDashboardService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Map<String, String> ORDER_STATUS_LABELS = Map.of(
            "CREATED", "已创建",
            "QUEUED", "排队中",
            "IN_PRODUCTION", "生产中",
            "COMPLETED", "已完成",
            "CANCELLED", "已取消");
    private static final Map<String, String> EQUIPMENT_STATUS_LABELS = Map.of(
            "IDLE", "空闲",
            "RUNNING", "生产中",
            "MAINTENANCE", "保养中",
            "FAULT", "故障维修",
            "DISABLED", "停用");

    private final JdbcTemplate jdbcTemplate;
    private final BiEtlService etlService;
    private final long refreshMs;

    public BiDashboardService(JdbcTemplate jdbcTemplate,
                              BiEtlService etlService,
                              com.company.platform.bi.config.BiProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.etlService = etlService;
        this.refreshMs = properties.getRefreshMs();
    }

    /** 生成当前用户可见的完整看板。 */
    public BiDashboardView dashboard() {
        LocalDateTime now = LocalDateTime.now();
        YearMonth currentMonth = YearMonth.from(now);
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        Long currentFactoryId = SecurityUtils.factoryId();
        String currentFactoryName = factoryName(currentFactoryId);
        BiScope scope = BiScope.current(currentFactoryName);

        boolean canOrder = has("order:read");
        boolean canReceipt = has("finance:receipt:read");
        boolean canReceivable = has("finance:receivable:read");
        boolean canCredit = has("finance:credit:read");
        boolean canExpense = has("finance:expense:read");
        boolean canLoan = has("finance:loan:read");
        boolean canApproval = has("approval:read");
        boolean canSchedule = has("production:schedule:read");
        boolean canEquipment = has("equipment:read");
        boolean canWarehouse = has("warehouse:read");

        BiScope.Filter orderFilter = scope.filter("factory_id", "owner_user_id");
        BiScope.Filter financeFilter = scope.filter("factory_id", "owner_user_id");
        // 报销和借款的归属人是申请人/借款人，不是订单创建人，列名与订单事实不同。
        BiScope.Filter expenseFilter = scope.filter("factory_id", "claimant_user_id");
        BiScope.Filter loanFilter = scope.filter("factory_id", "borrower_user_id");
        BiScope.Filter approvalFilter = scope.filter("factory_id", "applicant_user_id");
        BiScope.Filter assetFilter = scope.factoryOnly("factory_id");
        BiScope.Filter creditFilter = scope.creditFilter();

        List<BiDashboardView.MetricCard> cards = new ArrayList<>();
        List<BiDashboardView.RiskAlert> alerts = new ArrayList<>();

        long monthOrderAmount = 0L;
        long monthOrderCount = 0L;
        long openOrderCount = 0L;
        long monthReceipt = 0L;
        long receivableBalance = 0L;
        long overdueAmount = 0L;
        long overdueCount = 0L;
        long creditOverCount = 0L;
        long expensePendingAmount = 0L;
        long loanOutstanding = 0L;
        long pendingApproval = 0L;
        double averageApprovalHours = 0D;
        double queueWaitMinutes = 0D;
        long waitingQueueCount = 0L;
        long equipmentFaultCount = 0L;
        long inventoryAmount = 0L;
        long inventoryShortageCount = 0L;
        long transferInTransit = 0L;

        if (canOrder) {
            monthOrderAmount = sum("bi_fact_order", "total_amount",
                    "created_at >= ? AND created_at < ? AND order_status <> 'CANCELLED' AND "
                            + orderFilter.where(), args(monthStart, monthEnd, orderFilter));
            monthOrderCount = count("bi_fact_order",
                    "created_at >= ? AND created_at < ? AND order_status <> 'CANCELLED' AND "
                            + orderFilter.where(), args(monthStart, monthEnd, orderFilter));
            openOrderCount = count("bi_fact_order", "order_status NOT IN ('CANCELLED','COMPLETED') AND "
                    + orderFilter.where(), orderFilter.args());
            long completed = count("bi_fact_order",
                    "created_at >= ? AND created_at < ? AND order_status = 'COMPLETED' AND "
                            + orderFilter.where(), args(monthStart, monthEnd, orderFilter));
            String completion = monthOrderCount == 0 ? "0.00"
                    : BigDecimal.valueOf(completed * 100.0 / monthOrderCount)
                    .setScale(2, RoundingMode.DOWN).toPlainString();
            cards.add(moneyCard("month_order_amount", "本月订单额", monthOrderAmount,
                    "本月创建、未取消订单的金额合计", "teal", "/orders"));
            cards.add(countCard("month_order_count", "本月订单数", monthOrderCount, "笔",
                    "本月创建、未取消订单笔数", "blue", "/orders"));
            cards.add(percentCard("order_completion_rate", "订单完成率", completion,
                    "本月创建订单中已完成的比例", "violet", "/orders"));
            cards.add(countCard("open_order_count", "未关闭订单", openOrderCount, "笔",
                    "在制、排队、待排产订单", "amber", "/orders"));
        }

        if (canReceipt) {
            monthReceipt = sum("bi_fact_receipt", "amount",
                    "received_date >= ? AND received_date < ? AND status <> 'CANCELLED' AND "
                            + financeFilter.where(), args(currentMonth.atDay(1), currentMonth.plusMonths(1).atDay(1),
                            financeFilter));
            cards.add(moneyCard("month_receipt_amount", "本月收款", monthReceipt,
                    "按收款日期归属本月的收款单金额", "blue", "/finance/receipts"));
        }

        if (canReceivable) {
            receivableBalance = sum("bi_fact_receivable", "outstanding_amount",
                    "status <> 'CANCELLED' AND outstanding_amount > 0 AND " + financeFilter.where(),
                    financeFilter.args());
            overdueAmount = sum("bi_fact_receivable", "outstanding_amount",
                    "overdue_flag = 1 AND status <> 'CANCELLED' AND outstanding_amount > 0 AND "
                            + financeFilter.where(), financeFilter.args());
            overdueCount = count("bi_fact_receivable",
                    "overdue_flag = 1 AND status <> 'CANCELLED' AND outstanding_amount > 0 AND "
                            + financeFilter.where(), financeFilter.args());
            cards.add(moneyCard("receivable_balance", "应收余额", receivableBalance,
                    "未结清应收的未收金额合计", "amber", "/finance/receivables"));
            cards.add(moneyCard("overdue_receivable", "逾期应收", overdueAmount,
                    "到期日早于今天且仍有未收余额", "rose", "/finance/receivables"));
            if (overdueAmount > 0) {
                alerts.add(new BiDashboardView.RiskAlert("RECEIVABLE_OVERDUE", "应收逾期",
                        overdueCount + " 笔应收逾期，未收 " + money(overdueAmount),
                        overdueCount, "/finance/receivables", "HIGH"));
            }
        }

        if (canCredit) {
            creditOverCount = count("bi_customer_credit", "over_limit = 1 AND " + creditFilter.where(),
                    creditFilter.args());
            cards.add(countCard("credit_over_limit_customers", "信用超限客户", creditOverCount, "户",
                    "已用信用超过授信额度，0 额度不计", "violet", "/finance/credit"));
            if (creditOverCount > 0) {
                alerts.add(new BiDashboardView.RiskAlert("CREDIT_OVER_LIMIT", "信用超限",
                        creditOverCount + " 户客户已超过信用额度", creditOverCount,
                        "/finance/credit", "HIGH"));
            }
        }

        if (canExpense) {
            expensePendingAmount = sum("bi_fact_expense", "amount",
                    "status IN ('PENDING','APPROVED') AND " + expenseFilter.where(), expenseFilter.args());
            cards.add(moneyCard("expense_pending_amount", "待付款报销", expensePendingAmount,
                    "审批中或待付款的报销金额", "amber", "/finance/expenses"));
        }

        if (canLoan) {
            loanOutstanding = sum("bi_fact_loan", "outstanding_amount",
                    "status = 'DISBURSED' AND outstanding_amount > 0 AND " + loanFilter.where(),
                    loanFilter.args());
            cards.add(moneyCard("loan_outstanding", "借款未还", loanOutstanding,
                    "已放款且尚未还清的借款", "violet", "/finance/loans"));
        }

        if (canApproval) {
            pendingApproval = count("bi_fact_approval", "status = 'PENDING' AND " + approvalFilter.where(),
                    approvalFilter.args());
            Double avgSeconds = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(duration_seconds), 0) FROM bi_fact_approval "
                            + "WHERE duration_seconds IS NOT NULL AND status IN ('APPROVED','REJECTED') AND "
                            + approvalFilter.where(), Double.class, approvalFilter.args().toArray());
            averageApprovalHours = avgSeconds == null ? 0D : avgSeconds / 3600D;
            cards.add(countCard("pending_approval_count", "待审批", pendingApproval, "条",
                    "当前处于待审批状态的审批实例数", "rose", "/approval"));
            cards.add(new BiDashboardView.MetricCard("average_approval_hours", "平均审批时长",
                    null, "小时",
                    BigDecimal.valueOf(averageApprovalHours).setScale(2, RoundingMode.DOWN).toPlainString(),
                    "blue", "已完成审批从提交到结束的平均时长", "/approval"));
            long overdueApprovals = count("bi_fact_approval",
                    "status = 'PENDING' AND created_at < ? AND " + approvalFilter.where(),
                    args(now.minusHours(24), approvalFilter));
            if (overdueApprovals > 0) {
                alerts.add(new BiDashboardView.RiskAlert("APPROVAL_OVERDUE", "审批超时",
                        overdueApprovals + " 条待审批已超过 24 小时", overdueApprovals,
                        "/approval", "MEDIUM"));
            }
        }

        if (canSchedule) {
            Double avgWait = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(TIMESTAMPDIFF(MINUTE, enqueued_at, actual_start)), 0) "
                            + "FROM bi_fact_queue WHERE actual_start IS NOT NULL AND status IN ('RUNNING','DONE') AND "
                            + assetFilter.where(), Double.class, assetFilter.args().toArray());
            queueWaitMinutes = avgWait == null ? 0D : avgWait;
            waitingQueueCount = count("bi_fact_queue", "status = 'WAITING' AND " + assetFilter.where(),
                    assetFilter.args());
            cards.add(new BiDashboardView.MetricCard("equipment_queue_wait", "队列平均等待",
                    Math.round(queueWaitMinutes), "分钟",
                    String.valueOf(Math.round(queueWaitMinutes)), "blue",
                    "已开始生产队列从入队到实际开始的平均分钟", "/production/schedule"));
            cards.add(countCard("waiting_queue_count", "排队工单", waitingQueueCount, "个",
                    "等待设备空闲的工单数", "amber", "/production/schedule"));
            if (waitingQueueCount > 0) {
                alerts.add(new BiDashboardView.RiskAlert("QUEUE_WAITING", "设备排队",
                        waitingQueueCount + " 个工单等待设备空闲", waitingQueueCount,
                        "/production/schedule", "MEDIUM"));
            }
        }

        if (canEquipment) {
            equipmentFaultCount = count("bi_fact_equipment", "status = 'FAULT' AND " + assetFilter.where(),
                    assetFilter.args());
            cards.add(countCard("equipment_fault_count", "故障设备", equipmentFaultCount, "台",
                    "当前状态为故障维修的设备", "rose", "/equipment"));
            if (equipmentFaultCount > 0) {
                alerts.add(new BiDashboardView.RiskAlert("EQUIPMENT_FAULT", "设备故障",
                        equipmentFaultCount + " 台设备处于故障维修状态", equipmentFaultCount,
                        "/equipment", "HIGH"));
            }
        }

        if (canWarehouse) {
            inventoryAmount = sum("bi_fact_inventory", "amount", assetFilter.where(), assetFilter.args());
            inventoryShortageCount = countInventoryShortage(assetFilter);
            transferInTransit = countTransferInTransit(scope);
            cards.add(moneyCard("inventory_amount", "库存金额", inventoryAmount,
                    "按最近采购入库单价估算，无入库单价按 0", "green", "/warehouse"));
            cards.add(countCard("inventory_shortage_count", "缺料预警", inventoryShortageCount, "项",
                    "工厂物料库存低于安全库存的项数", "amber", "/warehouse"));
            if (inventoryShortageCount > 0) {
                alerts.add(new BiDashboardView.RiskAlert("INVENTORY_SHORTAGE", "库存缺料",
                        inventoryShortageCount + " 项物料低于安全库存", inventoryShortageCount,
                        "/warehouse", "MEDIUM"));
            }
            if (transferInTransit > 0) {
                alerts.add(new BiDashboardView.RiskAlert("TRANSFER_IN_TRANSIT", "调拨在途",
                        transferInTransit + " 张调拨单待出库或在途", transferInTransit,
                        "/warehouse/stock-transfers", "LOW"));
            }
        }

        LocalDateTime lastSuccess = etlService.lastSuccessAt();
        String dataStatus = "READY";
        if (lastSuccess == null) {
            dataStatus = "NO_DATA";
        } else if (Duration.between(lastSuccess, now).toMinutes() > Math.max(15, refreshMs / 60000 * 2)) {
            dataStatus = "STALE";
        }
        if ("STALE".equals(dataStatus)) {
            alerts.add(new BiDashboardView.RiskAlert("DATA_STALE", "数据刷新延迟",
                    "BI 最近一次成功同步时间较早，正在显示上一版快照", null, "", "LOW"));
        }

        BiDashboardView.OrderTrend trend = buildOrderTrend(currentMonth, canOrder, canReceipt,
                orderFilter, financeFilter);

        List<BiDashboardView.FactoryComparison> factoryComparison = buildFactoryComparison(
                scope, currentFactoryId, currentMonth, monthStart, monthEnd, orderFilter, assetFilter, canOrder);

        List<BiDashboardView.StatusBreakdown> orderStatus = canOrder
                ? buildStatusBreakdown("bi_fact_order", "order_status", "created_at >= ? AND created_at < ? AND "
                + orderFilter.where(), args(monthStart, monthEnd, orderFilter), ORDER_STATUS_LABELS)
                : List.of();
        List<BiDashboardView.StatusBreakdown> equipmentStatus = canEquipment || canSchedule
                ? buildStatusBreakdown("bi_fact_equipment", "status", assetFilter.where(),
                assetFilter.args(), EQUIPMENT_STATUS_LABELS)
                : List.of();

        List<BiDashboardView.MetricDefinition> pendingMetrics = loadPendingMetrics();
        String scopeMode = switch (scope.mode()) {
            case ORG -> "ORG";
            case FACTORY -> "FACTORY";
            case SELF -> "SELF";
        };
        String scopeLabel = switch (scope.mode()) {
            case ORG -> "全公司";
            case FACTORY -> "本厂 · " + (currentFactoryName == null ? "未绑定工厂" : currentFactoryName);
            case SELF -> "本人数据";
        };

        return new BiDashboardView(
                new BiDashboardView.Meta(now.format(DATE_TIME),
                        lastSuccess == null ? "尚未完成首次同步" : lastSuccess.format(DATE_TIME),
                        dataStatus, scopeMode, scopeLabel, currentMonth.format(MONTH),
                        String.valueOf(currentMonth.getYear()), Math.max(60, refreshMs / 1000)),
                cards,
                trend,
                factoryComparison,
                orderStatus,
                equipmentStatus,
                alerts.stream()
                        .sorted((left, right) -> severity(right.severity()) - severity(left.severity()))
                        .toList(),
                pendingMetrics);
    }

    /** 近 12 个月订单趋势；有收款权限时同时返回近 12 个月收款趋势。 */
    private BiDashboardView.OrderTrend buildOrderTrend(YearMonth currentMonth,
                                                       boolean canOrder,
                                                       boolean canReceipt,
                                                       BiScope.Filter orderFilter,
                                                       BiScope.Filter financeFilter) {
        List<String> months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            months.add(currentMonth.minusMonths(i).format(MONTH));
        }
        Map<String, Long> orderAmountByMonth = new LinkedHashMap<>();
        Map<String, Long> orderCountByMonth = new LinkedHashMap<>();
        Map<String, Long> receiptByMonth = new LinkedHashMap<>();
        if (canOrder) {
            LocalDateTime start = currentMonth.minusMonths(11).atDay(1).atStartOfDay();
            LocalDateTime end = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
            jdbcTemplate.query("SELECT DATE_FORMAT(created_at, '%Y-%m') AS period, "
                            + "COALESCE(SUM(total_amount), 0) AS amount, COUNT(*) AS cnt "
                            + "FROM bi_fact_order WHERE created_at >= ? AND created_at < ? "
                            + "AND order_status <> 'CANCELLED' AND " + orderFilter.where()
                            + " GROUP BY DATE_FORMAT(created_at, '%Y-%m') ORDER BY period",
                    rs -> {
                        orderAmountByMonth.put(rs.getString("period"), rs.getLong("amount"));
                        orderCountByMonth.put(rs.getString("period"), rs.getLong("cnt"));
                    }, args(start, end, orderFilter).toArray());
        }
        if (canReceipt) {
            LocalDate start = currentMonth.minusMonths(11).atDay(1);
            LocalDate end = currentMonth.plusMonths(1).atDay(1);
            jdbcTemplate.query("SELECT DATE_FORMAT(received_date, '%Y-%m') AS period, "
                            + "COALESCE(SUM(amount), 0) AS amount FROM bi_fact_receipt "
                            + "WHERE received_date >= ? AND received_date < ? AND status <> 'CANCELLED' AND "
                            + financeFilter.where() + " GROUP BY DATE_FORMAT(received_date, '%Y-%m') ORDER BY period",
                    rs -> {
                        receiptByMonth.put(rs.getString("period"), rs.getLong("amount"));
                    },
                    args(start, end, financeFilter).toArray());
        }
        return new BiDashboardView.OrderTrend(months,
                months.stream().map(month -> orderAmountByMonth.getOrDefault(month, 0L)).toList(),
                months.stream().map(month -> orderCountByMonth.getOrDefault(month, 0L)).toList(),
                months.stream().map(month -> receiptByMonth.getOrDefault(month, 0L)).toList());
    }

    /** 工厂对比；没有订单权限时返回空列表，前端不渲染该图。 */
    private List<BiDashboardView.FactoryComparison> buildFactoryComparison(BiScope scope,
                                                                           Long currentFactoryId,
                                                                           YearMonth currentMonth,
                                                                           LocalDateTime monthStart,
                                                                           LocalDateTime monthEnd,
                                                                           BiScope.Filter orderFilter,
                                                                           BiScope.Filter assetFilter,
                                                                           boolean canOrder) {
        if (!canOrder) {
            return List.of();
        }
        Map<Long, String> factoryNames = jdbcTemplate.query(
                "SELECT factory_id, factory_name FROM bi_dim_factory ORDER BY factory_id",
                rs -> {
                    Map<Long, String> names = new LinkedHashMap<>();
                    while (rs.next()) {
                        names.put(rs.getLong("factory_id"), rs.getString("factory_name"));
                    }
                    return names;
                });
        Map<Long, FactoryAccumulator> rows = new LinkedHashMap<>();
        if (scope.mode() == BiScope.Mode.ORG) {
            factoryNames.forEach((id, name) -> rows.put(id, new FactoryAccumulator(id, name)));
        } else if (scope.mode() == BiScope.Mode.FACTORY && currentFactoryId != null) {
            rows.put(currentFactoryId, new FactoryAccumulator(currentFactoryId,
                    factoryNames.getOrDefault(currentFactoryId, "本厂")));
        }

        jdbcTemplate.query("SELECT factory_id, COALESCE(SUM(total_amount), 0) AS amount, COUNT(*) AS cnt "
                        + "FROM bi_fact_order WHERE created_at >= ? AND created_at < ? "
                        + "AND order_status <> 'CANCELLED' AND " + orderFilter.where()
                        + " GROUP BY factory_id ORDER BY factory_id",
                rs -> {
                    Long id = rs.getLong("factory_id");
                    rows.computeIfAbsent(id, key -> new FactoryAccumulator(key,
                                    factoryNames.getOrDefault(key, "工厂 #" + key)))
                            .orderAmount = rs.getLong("amount");
                    rows.get(id).orderCount = rs.getLong("cnt");
                }, args(monthStart, monthEnd, orderFilter).toArray());

        jdbcTemplate.query("SELECT factory_id, COUNT(*) AS cnt FROM bi_fact_queue "
                        + "WHERE status = 'WAITING' AND " + assetFilter.where() + " GROUP BY factory_id",
                rs -> {
                    Long id = rs.getLong("factory_id");
                    rows.computeIfAbsent(id, key -> new FactoryAccumulator(key,
                            factoryNames.getOrDefault(key, "工厂 #" + key))).queueCount = rs.getLong("cnt");
                }, assetFilter.args().toArray());

        jdbcTemplate.query("SELECT factory_id, COUNT(*) AS cnt FROM bi_fact_equipment "
                        + "WHERE status = 'FAULT' AND " + assetFilter.where() + " GROUP BY factory_id",
                rs -> {
                    Long id = rs.getLong("factory_id");
                    rows.computeIfAbsent(id, key -> new FactoryAccumulator(key,
                            factoryNames.getOrDefault(key, "工厂 #" + key))).faultCount = rs.getLong("cnt");
                }, assetFilter.args().toArray());

        return rows.values().stream()
                .sorted((left, right) -> Long.compare(right.orderAmount, left.orderAmount))
                .map(row -> new BiDashboardView.FactoryComparison(row.factoryId, row.factoryName,
                        row.orderAmount, row.orderCount, row.queueCount, row.faultCount))
                .toList();
    }

    /** 按状态分组并补齐缺失状态，避免环形图颜色在不同刷新间跳动。 */
    private List<BiDashboardView.StatusBreakdown> buildStatusBreakdown(String table,
                                                                       String column,
                                                                       String where,
                                                                       List<Object> args,
                                                                       Map<String, String> labels) {
        Map<String, Long> counts = new LinkedHashMap<>();
        // RowCallbackHandler 由 Spring 负责推进结果集，回调里不能再调用 rs.next()。
        jdbcTemplate.query("SELECT " + column + " AS code, COUNT(*) AS cnt FROM " + table
                        + " WHERE " + where + " GROUP BY " + column,
                rs -> {
                    counts.put(rs.getString("code"), rs.getLong("cnt"));
                }, args.toArray());
        return labels.entrySet().stream()
                .map(entry -> new BiDashboardView.StatusBreakdown(entry.getKey(), entry.getValue(),
                        counts.getOrDefault(entry.getKey(), 0L)))
                .filter(row -> row.count() > 0)
                .toList();
    }

    /** 缺料按工厂 + 物料汇总，再与物料安全库存比较。 */
    private long countInventoryShortage(BiScope.Filter assetFilter) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ("
                        + "SELECT factory_id, material_id, SUM(quantity) AS qty, MAX(safety_stock) AS safety "
                        + "FROM bi_fact_inventory WHERE " + assetFilter.where()
                        + " GROUP BY factory_id, material_id) t WHERE t.qty < t.safety",
                Long.class, assetFilter.args().toArray());
        return value == null ? 0L : value;
    }

    /** 调拨在途需要同时考虑调出和调入工厂。 */
    private long countTransferInTransit(BiScope scope) {
        String where;
        List<Object> params = new ArrayList<>();
        if (scope.mode() == BiScope.Mode.ORG) {
            where = "status IN ('PENDING','IN_TRANSIT')";
        } else if (scope.factoryId() == null) {
            return 0L;
        } else {
            where = "status IN ('PENDING','IN_TRANSIT') AND (from_factory_id = ? OR to_factory_id = ?)";
            params.add(scope.factoryId());
            params.add(scope.factoryId());
        }
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bi_fact_transfer WHERE " + where,
                Long.class, params.toArray());
        return value == null ? 0L : value;
    }

    /** 待数据源指标；只返回当前用户有权查看的条目。 */
    private List<BiDashboardView.MetricDefinition> loadPendingMetrics() {
        return jdbcTemplate.query("SELECT metric_code, metric_name, category, unit, definition, source_note, "
                        + "status, permission FROM bi_metric WHERE status = 'PENDING_SOURCE' ORDER BY sort_no",
                (rs, rowNum) -> new BiDashboardView.MetricDefinition(
                        rs.getString("metric_code"),
                        rs.getString("metric_name"),
                        rs.getString("category"),
                        rs.getString("unit"),
                        rs.getString("definition"),
                        rs.getString("source_note"),
                        rs.getString("status"),
                        rs.getString("permission")))
                .stream()
                .filter(item -> item.permission() == null || item.permission().isBlank()
                        || has(item.permission()))
                .toList();
    }

    /** 查询所有指标口径，供前端弹窗展示。 */
    public List<BiDashboardView.MetricDefinition> metrics() {
        return jdbcTemplate.query("SELECT metric_code, metric_name, category, unit, definition, source_note, "
                        + "status, permission FROM bi_metric ORDER BY sort_no",
                (rs, rowNum) -> new BiDashboardView.MetricDefinition(
                        rs.getString("metric_code"),
                        rs.getString("metric_name"),
                        rs.getString("category"),
                        rs.getString("unit"),
                        rs.getString("definition"),
                        rs.getString("source_note"),
                        rs.getString("status"),
                        rs.getString("permission")))
                .stream()
                .filter(item -> item.permission() == null || item.permission().isBlank()
                        || has(item.permission()))
                .toList();
    }

    private String factoryName(Long factoryId) {
        if (factoryId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject("SELECT factory_name FROM bi_dim_factory WHERE factory_id = ?",
                    String.class, factoryId);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean has(String permission) {
        try {
            return SecurityUtils.hasPermission(permission);
        } catch (Exception ex) {
            return false;
        }
    }

    private long sum(String table, String column, String where, List<Object> args) {
        Long value = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM " + table
                + " WHERE " + where, Long.class, args.toArray());
        return value == null ? 0L : value;
    }

    private long count(String table, String where, List<Object> args) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where,
                Long.class, args.toArray());
        return value == null ? 0L : value;
    }

    private static List<Object> args(Object... values) {
        List<Object> args = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof BiScope.Filter filter) {
                args.addAll(filter.args());
            } else {
                args.add(value);
            }
        }
        return args;
    }

    private static BiDashboardView.MetricCard moneyCard(String code, String label, long cents,
                                                        String hint, String tone, String link) {
        return new BiDashboardView.MetricCard(code, label, cents, "元", money(cents), tone, hint, link);
    }

    private static BiDashboardView.MetricCard countCard(String code, String label, long value,
                                                        String unit, String hint, String tone, String link) {
        return new BiDashboardView.MetricCard(code, label, value, unit,
                NumberFormat.getIntegerInstance(Locale.CHINA).format(value), tone, hint, link);
    }

    private static BiDashboardView.MetricCard percentCard(String code, String label, String percent,
                                                          String hint, String tone, String link) {
        return new BiDashboardView.MetricCard(code, label, null, "%", percent + "%", tone, hint, link);
    }

    /** 金额格式：一万元以上用“万”展示，否则保留两位小数；始终直接截断。 */
    private static String money(long cents) {
        long abs = Math.abs(cents);
        if (abs >= 1_000_000L) {
            BigDecimal wan = BigDecimal.valueOf(cents, 2)
                    .divide(BigDecimal.valueOf(10_000L), 2, RoundingMode.DOWN);
            return "¥" + wan.toPlainString() + " 万";
        }
        BigDecimal yuan = BigDecimal.valueOf(cents, 2).setScale(2, RoundingMode.DOWN);
        return "¥" + new DecimalFormat("#,##0.00").format(yuan);
    }

    private static int severity(String value) {
        return switch (value == null ? "" : value) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static final class FactoryAccumulator {
        private final Long factoryId;
        private final String factoryName;
        private long orderAmount;
        private long orderCount;
        private long queueCount;
        private long faultCount;

        private FactoryAccumulator(Long factoryId, String factoryName) {
            this.factoryId = factoryId;
            this.factoryName = factoryName;
        }
    }
}
