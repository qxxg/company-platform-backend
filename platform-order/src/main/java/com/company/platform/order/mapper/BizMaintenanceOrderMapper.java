package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizMaintenanceOrder;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备维护工单 Mapper。
 *
 * <p>维护工单落在 ShardingSphere 单表 {@code t_maintenance_order}（固定 ds_0），
 * 因此这里的 SQL 不会被广播。开始与完成维护会先锁定工单行，避免并发重复
 * 变更设备状态。</p>
 */
@Mapper
public interface BizMaintenanceOrderMapper extends BaseMapper<BizMaintenanceOrder> {

    /** 在事务中锁定维护工单行，供状态流转使用。 */
    @Select("SELECT * FROM t_maintenance_order WHERE id = #{id} FOR UPDATE")
    BizMaintenanceOrder selectForUpdate(@Param("id") Long id);
}
