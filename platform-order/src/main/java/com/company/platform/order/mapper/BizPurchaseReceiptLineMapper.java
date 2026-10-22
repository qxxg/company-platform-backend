package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizPurchaseReceiptLine;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购入库明细 Mapper。
 *
 * <p>单表（固定 ds_0）。明细随入库单整体写入，已收货单据的明细不再变更。</p>
 */
@Mapper
public interface BizPurchaseReceiptLineMapper extends BaseMapper<BizPurchaseReceiptLine> {
}
