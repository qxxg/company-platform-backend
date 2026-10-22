package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizStockTransferLine;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 跨厂调拨明细 Mapper。
 *
 * <p>单表（固定 ds_0）。明细随调拨单整体写入，出库与入库时按明细逐条移动
 * 库存数量。</p>
 */
@Mapper
public interface BizStockTransferLineMapper extends BaseMapper<BizStockTransferLine> {
}
