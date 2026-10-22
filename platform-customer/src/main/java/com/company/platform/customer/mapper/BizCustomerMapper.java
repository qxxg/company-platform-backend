package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizCustomer;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户 Mapper。
 *
 * <p>客户查询含模糊搜索，统一在服务层拼装带参数的 SQL，避免把用户输入直接
 * 拼入 SQL 造成注入。分页由 MyBatis-Flex 生成 count 语句。</p>
 */
@Mapper
public interface BizCustomerMapper extends BaseMapper<BizCustomer> {
}
