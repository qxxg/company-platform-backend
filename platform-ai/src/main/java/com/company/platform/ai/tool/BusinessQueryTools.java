package com.company.platform.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * AI 助手的只读业务查询工具。
 *
 * <p>工具对象每次对话新建，内部持有当前用户的 JWT 和权限集合。业务数据不直接
 * 从数据库读取，而是调用订单、客户、审批服务已有的 REST 接口；目标服务会再次
 * 执行权限和数据范围校验。因此 AI 不是超级用户，也读不到用户本人在页面上
 * 看不到的数据。</p>
 *
 * <p>所有工具都没有参数，降低 4B 小模型生成错误 JSON 参数的概率；需要筛选时
 * 由工具内部固定取最近一页数据，并在回答中明确“最近 N 条”，不把样本合计
 * 冒充全量合计。</p>
 */
public class BusinessQueryTools {

    private static final Logger log = LoggerFactory.getLogger(BusinessQueryTools.class);

    private static final Map<String, String> ORDER_STATUS_NAMES = Map.ofEntries(
            Map.entry("CREATED", "已创建"),
            Map.entry("QUEUED", "已排队"),
            Map.entry("IN_PRODUCTION", "生产中"),
            Map.entry("COMPLETED", "已完成"),
            Map.entry("CANCELLED", "已取消"),
            Map.entry("SHIPPED", "已发货")
    );

    private static final Map<String, String> BUSINESS_TYPE_NAMES = Map.ofEntries(
            Map.entry("QUEUE_JUMP", "设备插队"),
            Map.entry("CREDIT_LIMIT", "信用额度"),
            Map.entry("EXPENSE", "费用报销"),
            Map.entry("LOAN", "借款")
    );

    private final RestTemplate restTemplate;
    private final String accessToken;
    private final Set<String> permissions;
    private final String orderUrl;
    private final String customerUrl;
    private final String approvalUrl;
    private final List<ToolTrace> traces = new CopyOnWriteArrayList<>();

    public BusinessQueryTools(RestTemplate restTemplate,
                              String accessToken,
                              Set<String> permissions,
                              String orderUrl,
                              String customerUrl,
                              String approvalUrl) {
        this.restTemplate = restTemplate;
        this.accessToken = accessToken;
        this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        this.orderUrl = orderUrl;
        this.customerUrl = customerUrl;
        this.approvalUrl = approvalUrl;
    }

    /** 本轮对话实际发生的工具调用，按发生顺序返回。 */
    public List<ToolTrace> traces() {
        return List.copyOf(traces);
    }

    /**
     * 查询订单概览。
     *
     * <p>用户询问订单数量、最近订单、订单状态或订单金额时使用。工具返回当前
     * 账号数据权限内的订单总数和最近 8 笔订单，不会返回全量明细。</p>
     */
    @Tool(name = "get_order_summary",
            description = "查询当前用户可见的订单总数和最近订单。用户询问订单、订单状态、订单金额、最近订单时使用。",
            readOnly = true)
    public String getOrderSummary() {
        return run("get_order_summary", "订单概览", () -> {
            String denied = requirePermission("order:read", "订单查看");
            if (denied != null) {
                return denied;
            }
            JsonNode data = getJson(orderUrl + "/api/order/orders/page?page=1&size=8");
            long total = data.path("total").asLong();
            JsonNode records = data.path("records");
            StringBuilder text = new StringBuilder();
            text.append("订单总数：").append(total).append(" 笔\n");
            if (!records.isArray() || records.isEmpty()) {
                text.append("最近没有订单记录。\n");
                return text.toString();
            }
            text.append("最近订单（最多 8 笔）：\n");
            long sampleTotal = 0;
            int shown = 0;
            for (JsonNode order : records) {
                shown++;
                long amount = order.path("totalAmount").asLong();
                sampleTotal += amount;
                text.append(shown).append(". 订单 ").append(text(order, "orderNo"))
                        .append("｜").append(ORDER_STATUS_NAMES.getOrDefault(text(order, "orderStatus"),
                                text(order, "orderStatus")))
                        .append("｜金额 ").append(yuan(amount))
                        .append("｜客户 ID ").append(text(order, "customerId"))
                        .append("｜工厂 ID ").append(text(order, "factoryId"))
                        .append("｜创建 ").append(text(order, "createdAt"))
                        .append("\n");
            }
            text.append("最近 ").append(shown).append(" 笔合计：").append(yuan(sampleTotal))
                    .append("（只是样本合计，不是全部订单总额）\n");
            text.append("说明：数据来自订单服务，已按当前账号的数据权限过滤。\n");
            return text.toString();
        });
    }

    /**
     * 查询应收账款概览。
     *
     * <p>返回筛选范围内的应收总额、已收、未收、逾期金额和逾期笔数，并列出
     * 最近逾期的几笔明细。金额均由业务服务按分计算，工具只做元展示。</p>
     */
    @Tool(name = "get_receivable_summary",
            description = "查询当前用户可见的应收账款汇总、未收金额、逾期金额和最近逾期明细。用户询问应收、欠款、回款、逾期时使用。",
            readOnly = true)
    public String getReceivableSummary() {
        return run("get_receivable_summary", "应收概览", () -> {
            String denied = requirePermission("finance:receivable:read", "应收账款查看");
            if (denied != null) {
                return denied;
            }
            JsonNode data = getJson(customerUrl + "/api/finance/receivables/page?page=1&size=20");
            JsonNode summary = data.path("summary");
            StringBuilder text = new StringBuilder();
            text.append("应收账款汇总：\n")
                    .append("- 应收总额：").append(yuan(summary.path("totalAmount").asLong())).append("\n")
                    .append("- 已收金额：").append(yuan(summary.path("receivedAmount").asLong())).append("\n")
                    .append("- 未收金额：").append(yuan(summary.path("outstandingAmount").asLong())).append("\n")
                    .append("- 逾期未收：").append(yuan(summary.path("overdueAmount").asLong()))
                    .append("（").append(summary.path("overdueCount").asLong()).append(" 笔）\n")
                    .append("- 未结清应收：").append(summary.path("openCount").asLong()).append(" 笔")
                    .append("，涉及客户 ").append(summary.path("customerCount").asLong()).append(" 户\n");

            JsonNode records = data.path("records");
            int shown = 0;
            if (records.isArray() && !records.isEmpty()) {
                text.append("最近逾期明细（最多 5 笔）：\n");
                for (JsonNode item : records) {
                    if (!item.path("overdue").asBoolean(false)) {
                        continue;
                    }
                    shown++;
                    text.append("- ").append(text(item, "receivableNo"))
                            .append("｜客户 ").append(text(item, "customerName"))
                            .append("｜未收 ").append(yuan(item.path("outstandingAmount").asLong()))
                            .append("｜到期 ").append(text(item, "dueDate"))
                            .append("｜逾期 ").append(item.path("overdueDays").asInt()).append(" 天\n");
                    if (shown >= 5) {
                        break;
                    }
                }
            }
            if (shown == 0) {
                if (summary.path("overdueAmount").asLong() > 0) {
                    text.append("最近 20 笔应收中没有逾期明细；请到应收账款页面按逾期筛选查看。\n");
                } else {
                    text.append("当前没有逾期应收。\n");
                }
            }
            text.append("说明：逾期按到期日早于今天且仍有未收余额计算；数据来自客户与财务服务。\n");
            return text.toString();
        });
    }

    /**
     * 查询费用报销概览。
     *
     * <p>返回报销单数量、审批中/待付款金额和最近报销单，便于财务或员工
     * 快速了解费用处理进度。工具不执行付款和审批。</p>
     */
    @Tool(name = "get_expense_summary",
            description = "查询当前用户可见的费用报销汇总和最近报销单。用户询问报销、费用、待付款、报销进度时使用。",
            readOnly = true)
    public String getExpenseSummary() {
        return run("get_expense_summary", "费用报销概览", () -> {
            String denied = requirePermission("finance:expense:read", "费用报销查看");
            if (denied != null) {
                return denied;
            }
            JsonNode data = getJson(customerUrl + "/api/finance/expenses/page?page=1&size=20");
            JsonNode summary = data.path("summary");
            StringBuilder text = new StringBuilder();
            text.append("费用报销汇总：\n")
                    .append("- 单据总数：").append(summary.path("count").asLong()).append(" 笔\n")
                    .append("- 审批中：").append(summary.path("pendingCount").asLong()).append(" 笔，金额 ")
                    .append(yuan(summary.path("pendingAmount").asLong())).append("\n")
                    .append("- 待付款：").append(summary.path("approvedCount").asLong()).append(" 笔，金额 ")
                    .append(yuan(summary.path("approvedAmount").asLong())).append("\n")
                    .append("- 已付款：").append(summary.path("paidCount").asLong()).append(" 笔，金额 ")
                    .append(yuan(summary.path("paidAmount").asLong())).append("\n");

            JsonNode records = data.path("records");
            if (!records.isArray() || records.isEmpty()) {
                text.append("最近没有报销单。\n");
                return text.toString();
            }
            text.append("最近报销单（最多 8 笔）：\n");
            int shown = 0;
            for (JsonNode item : records) {
                shown++;
                text.append("- ").append(text(item, "claimNo"))
                        .append("｜").append(text(item, "claimantName"))
                        .append("｜").append(text(item, "expenseTypeName"))
                        .append("｜").append(yuan(item.path("amount").asLong()))
                        .append("｜").append(text(item, "statusName"))
                        .append("｜日期 ").append(text(item, "expenseDate")).append("\n");
                if (shown >= 8) {
                    break;
                }
            }
            text.append("说明：金额单位为元，数据来自费用报销服务；AI 不执行付款或审批。\n");
            return text.toString();
        });
    }

    /**
     * 查询借款与还款概览。
     *
     * <p>只汇总借款单数量、已放款未还金额、累计已还和最近借款单；报销冲销借款的明细需要
     * 进入借款详情查看，这里不展开。</p>
     */
    @Tool(name = "get_loan_summary",
            description = "查询当前用户可见的借款汇总、未还金额和最近借款单。用户询问借款、还款、未还金额时使用。",
            readOnly = true)
    public String getLoanSummary() {
        return run("get_loan_summary", "借款概览", () -> {
            String denied = requirePermission("finance:loan:read", "借款查看");
            if (denied != null) {
                return denied;
            }
            JsonNode data = getJson(customerUrl + "/api/finance/loans/page?page=1&size=20");
            JsonNode summary = data.path("summary");
            StringBuilder text = new StringBuilder();
            text.append("借款汇总：\n")
                    .append("- 借款单总数：").append(summary.path("count").asLong()).append(" 笔\n")
                    .append("- 还款中（已放款）：").append(summary.path("activeCount").asLong()).append(" 笔\n")
                    .append("- 已还清：").append(summary.path("repaidCount").asLong()).append(" 笔\n")
                    .append("- 借款申请总额（全部状态）：").append(yuan(summary.path("totalAmount").asLong())).append("\n")
                    .append("- 已放款未还：").append(yuan(summary.path("outstandingAmount").asLong())).append("\n")
                    .append("- 累计已还：").append(yuan(summary.path("repaidAmount").asLong())).append("\n")
                    .append("说明：未还金额只统计已放款借款；草稿、审批中、待放款不计入未还。\n");

            JsonNode records = data.path("records");
            if (!records.isArray() || records.isEmpty()) {
                text.append("最近没有借款单。\n");
                return text.toString();
            }
            text.append("最近借款单（最多 8 笔）：\n");
            int shown = 0;
            for (JsonNode item : records) {
                shown++;
                text.append("- ").append(text(item, "loanNo"))
                        .append("｜").append(text(item, "borrowerName"))
                        .append("｜借款 ").append(yuan(item.path("amount").asLong()))
                        .append("｜").append(text(item, "statusName"));
                if ("DISBURSED".equals(text(item, "status"))) {
                    text.append("｜未还 ").append(yuan(item.path("outstandingAmount").asLong()));
                } else {
                    text.append("｜未还 —");
                }
                text.append("｜日期 ").append(text(item, "loanDate")).append("\n");
                if (shown >= 8) {
                    break;
                }
            }
            text.append("说明：数据来自借款与还款服务；AI 不执行放款或还款登记。\n");
            return text.toString();
        });
    }

    /**
     * 查询当前审批人的待办。
     *
     * <p>只有拥有 {@code approval:approve} 的角色可以调用。返回待办数量、
     * 单据标题、金额和申请人，不返回完整审批轨迹。</p>
     */
    @Tool(name = "get_approval_todo_summary",
            description = "查询当前用户作为审批人的待办审批。用户询问待办、审批、需要我处理什么时使用。",
            readOnly = true)
    public String getApprovalTodoSummary() {
        return run("get_approval_todo_summary", "我的审批待办", () -> {
            String denied = requirePermission("approval:approve", "审批待办查看");
            if (denied != null) {
                return denied;
            }
            JsonNode data = getJson(approvalUrl + "/api/approval/todos/page?page=1&size=10");
            JsonNode summary = data.path("summary");
            StringBuilder text = new StringBuilder();
            text.append("我的审批待办：").append(summary.path("pending").asLong()).append(" 条\n");
            JsonNode records = data.path("records");
            if (!records.isArray() || records.isEmpty()) {
                text.append("当前没有待审批事项。\n");
                return text.toString();
            }
            text.append("待办明细（最多 10 条）：\n");
            int shown = 0;
            for (JsonNode item : records) {
                shown++;
                text.append("- ").append(text(item, "title"))
                        .append("｜").append(BUSINESS_TYPE_NAMES.getOrDefault(text(item, "businessType"),
                                text(item, "businessType")))
                        .append("｜申请人 ").append(text(item, "applicantName"))
                        .append("｜金额 ").append(yuan(item.path("amount").asLong()))
                        .append("｜提交 ").append(text(item, "createdAt")).append("\n");
            }
            text.append("说明：数据来自审批中心；AI 只读，不会代替你同意或驳回。\n");
            return text.toString();
        });
    }

    /** 统一执行、计时和异常处理，保证工具永远返回文本而不是把异常抛给模型。 */
    private String run(String name, String label, Supplier<String> action) {
        long startedAt = System.currentTimeMillis();
        try {
            String result = action.get();
            boolean success = !(result.startsWith("当前账号没有") || result.startsWith("查询失败"));
            traces.add(new ToolTrace(name, label, success,
                    System.currentTimeMillis() - startedAt, abbreviate(result)));
            return result;
        } catch (RestClientResponseException ex) {
            String message;
            int status = ex.getStatusCode().value();
            if (status == 401) {
                message = "查询失败：登录状态已失效，请重新登录后再试。";
            } else if (status == 403) {
                message = "当前账号没有「" + label + "」权限，无法查询该类数据。";
            } else {
                message = "查询失败：业务服务返回 HTTP " + status + "，请稍后再试。";
            }
            log.warn("AI tool {} failed with HTTP {}: {}", name, status, ex.getResponseBodyAsString());
            traces.add(new ToolTrace(name, label, false,
                    System.currentTimeMillis() - startedAt, message));
            return message;
        } catch (Exception ex) {
            log.warn("AI tool {} failed", name, ex);
            String message = "查询失败：业务服务暂时不可用，请稍后再试。";
            traces.add(new ToolTrace(name, label, false,
                    System.currentTimeMillis() - startedAt, message));
            return message;
        }
    }

    private String requirePermission(String permission, String label) {
        if (!permissions.contains(permission)) {
            return "当前账号没有「" + label + "」权限，无法查询该类数据。";
        }
        return null;
    }

    /** 调用内部业务接口并解包统一 ApiResponse。 */
    private JsonNode getJson(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode root = response.getBody();
        if (root == null) {
            throw new IllegalStateException("业务服务返回空响应");
        }
        if (root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException(root.path("message").asText("业务查询失败"));
        }
        return root.path("data");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "-";
        }
        String text = value.asText();
        return text == null || text.isBlank() ? "-" : text;
    }

    private static String yuan(long cents) {
        return BigDecimal.valueOf(cents, 2)
                .setScale(2, RoundingMode.DOWN)
                .toPlainString() + " 元";
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace('\n', ' ').trim();
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300) + "…";
    }
}
