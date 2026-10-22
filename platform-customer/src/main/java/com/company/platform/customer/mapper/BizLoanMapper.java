package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizLoan;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 借款单 Mapper。
 *
 * <p>还款（含报销冲销）先锁定借款单，再用带余额条件的原子更新累加已还金额，
 * 防止并发还款把未还金额减成负数。</p>
 */
@Mapper
public interface BizLoanMapper extends BaseMapper<BizLoan> {

    /** 在事务中锁定借款单。 */
    @Select("SELECT * FROM biz_loan WHERE id = #{id} FOR UPDATE")
    BizLoan selectForUpdate(@Param("id") Long id);

    /**
     * 原子登记一笔还款并更新借款状态。
     *
     * <p>条件确保借款处于已放款状态且余额足够；返回 0 表示余额已变化或状态
     * 不允许还款，调用方应回滚整个事务。</p>
     */
    @Update("""
            UPDATE biz_loan
            SET repaid_amount = repaid_amount + #{amount},
                outstanding_amount = outstanding_amount - #{amount},
                status = CASE WHEN outstanding_amount - #{amount} <= 0 THEN 'REPAID' ELSE 'DISBURSED' END,
                updated_at = NOW(3)
            WHERE id = #{id}
              AND status = 'DISBURSED'
              AND outstanding_amount >= #{amount}
            """)
    int addRepayment(@Param("id") Long id, @Param("amount") Long amount);
}
