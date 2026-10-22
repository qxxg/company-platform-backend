package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizLoanRepayment;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 借款还款记录 Mapper。
 *
 * <p>还款记录只追加，不提供修改和删除接口。</p>
 */
@Mapper
public interface BizLoanRepaymentMapper extends BaseMapper<BizLoanRepayment> {
}
