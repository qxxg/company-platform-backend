package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizReceiptAllocation;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款核销明细 Mapper。
 *
 * <p>核销明细只追加、不修改、不物理删除；查询时按收款单或应收汇总。</p>
 */
@Mapper
public interface BizReceiptAllocationMapper extends BaseMapper<BizReceiptAllocation> {

    /**
     * 汇总某客户在指定时间点之前的核销金额，用于对账单期初余额。
     *
     * <p>核销明细没有客户字段，通过应收归属客户关联；应收一定先于核销创建，
     * 所以早于期初的核销必然对应早于期初的应收。</p>
     */
    @Select("""
            SELECT COALESCE(SUM(a.amount), 0)
            FROM biz_receipt_allocation a
                     JOIN biz_receivable r ON r.id = a.receivable_id
            WHERE r.customer_id = #{customerId}
              AND a.created_at < #{before}
            """)
    long sumAllocatedBefore(@Param("customerId") Long customerId, @Param("before") LocalDateTime before);

    /**
     * 查询某客户在期间内发生的核销明细，供对账单明细使用。
     *
     * <p>按核销时间排序，保证同一业务日期的明细顺序稳定；这里使用
     * {@code a.*}，返回列名由 {@code map-underscore-to-camel-case} 自动映射。</p>
     */
    @Select("""
            SELECT a.*
            FROM biz_receipt_allocation a
                     JOIN biz_receivable r ON r.id = a.receivable_id
            WHERE r.customer_id = #{customerId}
              AND a.created_at >= #{start}
              AND a.created_at < #{end}
            ORDER BY a.created_at, a.id
            """)
    List<BizReceiptAllocation> selectInPeriod(@Param("customerId") Long customerId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);
}
