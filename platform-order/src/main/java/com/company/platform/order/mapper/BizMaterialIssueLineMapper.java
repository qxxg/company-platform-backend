package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizMaterialIssueLine;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单领料明细 Mapper。
 *
 * <p>单表（固定 ds_0）。明细随领料单整体写入，工单材料成本由已发料单据的明细
 * 汇总得出。</p>
 */
@Mapper
public interface BizMaterialIssueLineMapper extends BaseMapper<BizMaterialIssueLine> {
}
