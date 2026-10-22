package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizContract;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同 Mapper。
 *
 * <p>合同查询按客户、状态和编号过滤；合同金额只存分，剩余额度由订单侧计算，
 * 不在本表维护易失真的冗余字段。</p>
 */
@Mapper
public interface BizContractMapper extends BaseMapper<BizContract> {
}
