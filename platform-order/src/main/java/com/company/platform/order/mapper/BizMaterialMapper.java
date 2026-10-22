package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizMaterial;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物料档案 Mapper。
 *
 * <p>物料是单表（固定 ds_0）。单价、损耗率直接由物料档案提供，收货时快照到
 * 入库明细，历史单据不受档案后续修改影响。</p>
 */
@Mapper
public interface BizMaterialMapper extends BaseMapper<BizMaterial> {
}
