package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizReceipt;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 收款单 Mapper。
 *
 * <p>核销时先锁定收款单，保证同一张收款单不会被并发超额分配；累加更新带
 * 条件校验，作为数据库层最后一道防线。</p>
 */
@Mapper
public interface BizReceiptMapper extends BaseMapper<BizReceipt> {

    /** 在事务中锁定收款单，防止并发核销超收。 */
    @Select("SELECT * FROM biz_receipt WHERE id = #{id} FOR UPDATE")
    BizReceipt selectForUpdate(@Param("id") Long id);

    /**
     * 原子增加已核销金额并更新收款单状态。
     *
     * <p>条件确保未取消且累计核销不超过收款金额。返回 0 表示收款单已作废、
     * 已核销完或余额不足，调用方应回滚整个事务。</p>
     */
    @Update("""
            UPDATE biz_receipt
            SET status = CASE WHEN allocated_amount + #{amount} >= amount THEN 'ALLOCATED' ELSE 'PARTIAL' END,
                allocated_amount = allocated_amount + #{amount},
                updated_at = NOW(3)
            WHERE id = #{id}
              AND status <> 'CANCELLED'
              AND allocated_amount + #{amount} <= amount
            """)
    int addAllocatedAmount(@Param("id") Long id, @Param("amount") Long amount);
}
