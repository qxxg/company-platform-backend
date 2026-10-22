package com.company.platform.approval.mapper;

import com.company.platform.approval.entity.ApprovalRecord;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批记录 Mapper。
 *
 * <p>记录只追加，不提供更新或删除接口。</p>
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {
}
