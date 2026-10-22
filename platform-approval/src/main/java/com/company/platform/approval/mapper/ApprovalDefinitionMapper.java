package com.company.platform.approval.mapper;

import com.company.platform.approval.entity.ApprovalDefinition;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批定义 Mapper。
 *
 * <p>审批定义属于业务主数据，保存在 {@code company_platform}，不参与订单分片。</p>
 */
@Mapper
public interface ApprovalDefinitionMapper extends BaseMapper<ApprovalDefinition> {
}
