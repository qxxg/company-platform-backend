package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizReceivable;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 应收 Mapper。
 *
 * <p>除常规 CRUD 外，提供核销所需的行锁查询和原子累加更新。核销在事务内
 * 先按应收 ID 升序加锁，避免两张收款单同时核销同一应收时产生超收。</p>
 */
@Mapper
public interface BizReceivableMapper extends BaseMapper<BizReceivable> {

    /**
     * 按主键加排他锁查询应收。
     *
     * <p>必须在事务中调用；锁按应收 ID 升序获取，降低死锁概率。</p>
     */
    @Select("SELECT * FROM biz_receivable WHERE id = #{id} FOR UPDATE")
    BizReceivable selectForUpdate(@Param("id") Long id);

    /**
     * 原子增加已收金额并更新状态。
     *
     * <p>WHERE 条件再次校验“未取消且累计不超过应收金额”，即使调用方校验后
     * 被其他事务抢先核销，也不会出现超收。MySQL 单表 UPDATE 按从左到右
     * 赋值，先根据旧 received_amount 计算 status，再累加金额。</p>
     *
     * @return 受影响行数；0 表示应收已结清、已取消或金额不足
     */
    @Update("""
            UPDATE biz_receivable
            SET status = CASE WHEN received_amount + #{amount} >= amount THEN 'PAID' ELSE 'PARTIAL' END,
                received_amount = received_amount + #{amount},
                updated_at = NOW(3)
            WHERE id = #{id}
              AND status <> 'CANCELLED'
              AND received_amount + #{amount} <= amount
            """)
    int addReceivedAmount(@Param("id") Long id, @Param("amount") Long amount);
}
