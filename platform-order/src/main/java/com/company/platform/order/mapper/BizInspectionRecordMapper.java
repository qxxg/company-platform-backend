package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizInspectionRecord;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备点检保养记录 Mapper。
 *
 * <p>点检记录落在 ShardingSphere 单表 {@code t_inspection_record}（固定 ds_0），
 * 不参与分片路由。异常点检生成维护工单时会回写 {@code inspectionId} 到工单，
 * 便于从工单回溯点检明细。</p>
 */
@Mapper
public interface BizInspectionRecordMapper extends BaseMapper<BizInspectionRecord> {
}
