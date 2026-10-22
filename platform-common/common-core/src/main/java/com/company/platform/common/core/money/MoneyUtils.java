package com.company.platform.common.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额换算工具。
 *
 * <p>V1.4 明确规定所有金额以整数分存储，任何小数转分都必须直接截断，
 * 禁止四舍五入。本类只负责“元 → 分”和“单价 × 数量 → 分”的边界换算，
 * 不承担业务折扣、税率或汇率计算。</p>
 *
 * <p>使用 {@link RoundingMode#DOWN} 而不是 {@code HALF_UP}：
 * 例如 12.345 元只能得到 1234 分，不能得到 1235 分。</p>
 */
public final class MoneyUtils {

    private MoneyUtils() {
    }

    /**
     * 元转分，直接截断。
     *
     * @throws IllegalArgumentException 金额为空或超出 long 范围
     */
    public static long toCents(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /**
     * 单价 × 数量后转分。
     *
     * <p>先做乘法再截断，避免先把单价截断到分造成累计误差。单价仍要求
     * 调用方按业务精度（最多 4 位小数）传入。</p>
     */
    public static long multiplyToCents(BigDecimal unitPrice, BigDecimal quantity) {
        if (unitPrice == null || quantity == null) {
            throw new IllegalArgumentException("unitPrice and quantity must not be null");
        }
        return unitPrice.multiply(quantity)
                .movePointRight(2)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    /** 分转元，仅用于展示；不要用返回值继续参与金额计算。 */
    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
