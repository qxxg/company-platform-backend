package com.company.platform.approval.mapper;

import com.company.platform.approval.entity.ApprovalTask;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审批任务 Mapper。
 *
 * <p>待办查询按“指定审批人”或“审批角色”命中；处理前锁定任务行，避免重复审批。</p>
 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    /** 在事务中锁定审批任务行。 */
    @Select("SELECT * FROM approval_task WHERE id = #{id} FOR UPDATE")
    ApprovalTask selectForUpdate(@Param("id") Long id);
}
