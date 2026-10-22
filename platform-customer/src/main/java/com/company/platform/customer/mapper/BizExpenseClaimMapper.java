package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizExpenseClaim;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 费用报销单 Mapper。
 *
 * <p>付款与审批回调会并发修改同一张报销单，事务内先锁定行，避免重复付款
 * 或“审批驳回后又付款”的中间状态。</p>
 */
@Mapper
public interface BizExpenseClaimMapper extends BaseMapper<BizExpenseClaim> {

    /** 在事务中锁定报销单。 */
    @Select("SELECT * FROM biz_expense_claim WHERE id = #{id} FOR UPDATE")
    BizExpenseClaim selectForUpdate(@Param("id") Long id);
}
