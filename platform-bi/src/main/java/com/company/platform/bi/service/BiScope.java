package com.company.platform.bi.service;

import com.company.platform.common.security.SecurityUtils;

import java.util.List;

/**
 * BI 数据范围。
 *
 * <p>口径与现有业务服务对齐：管理员/财务/机器令牌看全公司；厂长看本厂；销售、
 * 操作员看本人数据。BI 不能因为做聚合就绕过业务数据范围，因此每条事实查询都要
 * 显式追加 scope 条件，而不是先查全量再在 Java 里裁剪。</p>
 */
public final class BiScope {

    public enum Mode {
        ORG,
        FACTORY,
        SELF
    }

    private final Mode mode;
    private final String label;
    private final Long factoryId;
    private final Long userId;

    private BiScope(Mode mode, String label, Long factoryId, Long userId) {
        this.mode = mode;
        this.label = label;
        this.factoryId = factoryId;
        this.userId = userId;
    }

    /** 根据当前 JWT 构建范围；工厂名称由调用方补充到 label。 */
    public static BiScope current(String factoryName) {
        if (SecurityUtils.isServiceAccount()
                || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return new BiScope(Mode.ORG, "全公司", SecurityUtils.factoryId(), SecurityUtils.userId());
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            String name = factoryName == null ? "本厂" : factoryName;
            return new BiScope(Mode.FACTORY, name, SecurityUtils.factoryId(), SecurityUtils.userId());
        }
        return new BiScope(Mode.SELF, "本人数据", SecurityUtils.factoryId(), SecurityUtils.userId());
    }

    public Mode mode() {
        return mode;
    }

    public String label() {
        return label;
    }

    public Long factoryId() {
        return factoryId;
    }

    public Long userId() {
        return userId;
    }

    /**
     * 按“工厂字段/归属人字段”生成条件。
     *
     * <p>适用于订单、应收、费用、借款、审批等同时带 factory_id 和 owner_user_id 的
     * 事实表。厂长按工厂，销售按归属人；管理员/财务不加条件。</p>
     */
    public Filter filter(String factoryColumn, String ownerColumn) {
        return switch (mode) {
            case ORG -> new Filter("1=1", List.of());
            case FACTORY -> new Filter(factoryColumn + " = ?",
                    List.of(factoryId == null ? -1L : factoryId));
            case SELF -> new Filter(ownerColumn + " = ?",
                    List.of(userId == null ? -1L : userId));
        };
    }

    /**
     * 仅按工厂范围过滤的资产类数据（设备、库存、队列、调拨）。
     *
     * <p>销售/操作员没有工厂时返回恒假条件，避免误把全部工厂资产暴露出去。</p>
     */
    public Filter factoryOnly(String factoryColumn) {
        return switch (mode) {
            case ORG -> new Filter("1=1", List.of());
            case FACTORY -> new Filter(factoryColumn + " = ?",
                    List.of(factoryId == null ? -1L : factoryId));
            case SELF -> factoryId == null
                    ? new Filter("1=0", List.of())
                    : new Filter(factoryColumn + " = ?", List.of(factoryId));
        };
    }

    /**
     * 客户信用按工厂关联：厂长只看本厂订单涉及的客户；销售看本人归属客户；
     * 管理员/财务看全部。客户本身没有工厂字段，所以用订单客户集合做桥接。
     */
    public Filter creditFilter() {
        return switch (mode) {
            case ORG -> new Filter("1=1", List.of());
            case FACTORY -> new Filter("customer_id IN (SELECT DISTINCT customer_id FROM bi_fact_order "
                    + "WHERE factory_id = ?)", List.of(factoryId == null ? -1L : factoryId));
            case SELF -> new Filter("owner_user_id = ?", List.of(userId == null ? -1L : userId));
        };
    }

    /** SQL 片段与参数；片段不包含前导 AND。 */
    public record Filter(String where, List<Object> args) {
    }
}
