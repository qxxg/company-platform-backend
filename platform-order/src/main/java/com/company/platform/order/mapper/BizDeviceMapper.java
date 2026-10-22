package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizDevice;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 设备台账 Mapper。
 *
 * <p>设备在 ShardingSphere 中是单表（固定 ds_0），因此这里的 SQL 不会被
 * 广播。改状态、开始/完成工单时先用 {@link #selectForUpdate(Long)} 锁定设备，
 * 保证同一设备不会被并发分配两个生产工单。</p>
 */
@Mapper
public interface BizDeviceMapper extends BaseMapper<BizDevice> {

    /** 在事务中锁定设备行，供状态变更和工单开始/完成使用。 */
    @Select("SELECT * FROM t_device WHERE id = #{id} FOR UPDATE")
    BizDevice selectForUpdate(@Param("id") Long id);

    /**
     * 工单完成后释放设备。
     *
     * <p>MyBatis-Flex 的默认 update 会忽略 null 字段，不能把 current_work_order_id
     * 清空；这里用显式 SQL 同时更新状态、当前工单、审计字段和版本号。</p>
     */
    @Update("""
            UPDATE t_device
            SET status = 'IDLE',
                current_work_order_id = NULL,
                updated_by = #{updatedBy},
                updated_at = #{updatedAt},
                version = version + 1
            WHERE id = #{id}
            """)
    int releaseAfterWorkOrder(@Param("id") Long id,
                              @Param("updatedBy") Long updatedBy,
                              @Param("updatedAt") LocalDateTime updatedAt);
}
