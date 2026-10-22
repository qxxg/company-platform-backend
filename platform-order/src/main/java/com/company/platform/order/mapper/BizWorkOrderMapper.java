package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizWorkOrder;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 生产工单 Mapper。
 *
 * <p>工单表是 ds_0 单表。订单号条件会跨分片表查订单，但工单本身不跨库；
 * 按订单号查询工单时直接使用普通索引。</p>
 */
@Mapper
public interface BizWorkOrderMapper extends BaseMapper<BizWorkOrder> {

    /** 在事务中锁定工单，供开始、完成、取消和插队审批使用。 */
    @Select("SELECT * FROM t_work_order WHERE id = #{id} FOR UPDATE")
    BizWorkOrder selectForUpdate(@Param("id") Long id);

    /** 查询某订单的全部工单，按创建时间升序，供订单详情汇总进度。 */
    @Select("SELECT * FROM t_work_order WHERE order_no = #{orderNo} ORDER BY created_at, id")
    List<BizWorkOrder> selectByOrderNo(@Param("orderNo") Long orderNo);
}
