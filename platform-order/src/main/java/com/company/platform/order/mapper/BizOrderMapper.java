package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizOrder;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper。物理表由 ShardingSphere-JDBC 在 SQL 执行阶段改写，
 * 业务代码始终面向逻辑表 {@code t_order}。
 *
 * <p>MyBatis-Flex 的分页会在 ShardingSphere 之上生成 count + limit 查询；
 * 订单号等值条件会精确路由，其余条件会广播到 8 张物理表后合并。</p>
 */
@Mapper
public interface BizOrderMapper extends BaseMapper<BizOrder> {
}
