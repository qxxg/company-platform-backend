package com.company.platform.common.core.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 金额换算规则测试。
 *
 * <p>V1.4 要求金额以分存储、计算小数直接截断，绝不四舍五入。
 * 这些用例是财务链路的底线，修改 MoneyUtils 时必须保持通过。</p>
 */
class MoneyUtilsTest {

    @Test
    void toCentsTruncatesInsteadOfRounding() {
        assertEquals(1234L, MoneyUtils.toCents(new BigDecimal("12.345")));
        assertEquals(199L, MoneyUtils.toCents(new BigDecimal("1.999")));
        assertEquals(0L, MoneyUtils.toCents(new BigDecimal("0.009")));
        assertEquals(1L, MoneyUtils.toCents(new BigDecimal("0.01")));
    }

    @Test
    void multiplyToCentsUsesFullPrecisionThenTruncates() {
        // 1.2345 元/米 × 2.5 米 = 3.08625 元，最终只能得到 308 分。
        assertEquals(308L, MoneyUtils.multiplyToCents(
                new BigDecimal("1.2345"), new BigDecimal("2.5")));
        // 先乘后截断，不能把单价先截成 1.23 再乘，否则会少 1 分。
        assertEquals(308L, MoneyUtils.multiplyToCents(
                new BigDecimal("1.2345"), new BigDecimal("2.50")));
    }

    @Test
    void fromCentsIsOnlyForDisplay() {
        assertEquals(new BigDecimal("12.34"), MoneyUtils.fromCents(1234L));
        assertEquals(new BigDecimal("0.00"), MoneyUtils.fromCents(0L));
    }
}
