package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizQueueJumpRequest;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 插队申请 Mapper。
 *
 * <p>审批时锁定申请单，防止同一申请被重复同意或驳回；同一工单的待审批申请
 * 在服务层做唯一性校验。</p>
 */
@Mapper
public interface BizQueueJumpRequestMapper extends BaseMapper<BizQueueJumpRequest> {

    /** 在事务中锁定申请单。 */
    @Select("SELECT * FROM t_queue_jump_request WHERE id = #{id} FOR UPDATE")
    BizQueueJumpRequest selectForUpdate(@Param("id") Long id);

    /** 统计某工单是否已有待审批申请。 */
    @Select("""
            SELECT COUNT(*) FROM t_queue_jump_request
            WHERE work_order_id = #{workOrderId} AND status = 'PENDING'
            """)
    long countPendingByWorkOrder(@Param("workOrderId") Long workOrderId);
}
