package com.company.platform.approval.mapper;

import com.company.platform.approval.entity.ApprovalInstance;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审批实例 Mapper。
 *
 * <p>审批动作会并发修改实例状态，因此处理前先锁定实例行。</p>
 */
@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstance> {

    /** 在事务中锁定审批实例行。 */
    @Select("SELECT * FROM approval_instance WHERE id = #{id} FOR UPDATE")
    ApprovalInstance selectForUpdate(@Param("id") Long id);
}
