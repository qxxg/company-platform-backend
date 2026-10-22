package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizPurchaseReceipt;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购入库单 Mapper。
 *
 * <p>单表（固定 ds_0）。确认收货会先锁单据行，避免同一张单被重复收货导致库存
 * 重复增加。</p>
 */
@Mapper
public interface BizPurchaseReceiptMapper extends BaseMapper<BizPurchaseReceipt> {

    /** 在事务中锁定入库单行。 */
    @Select("SELECT * FROM t_purchase_receipt WHERE id = #{id} FOR UPDATE")
    BizPurchaseReceipt selectForUpdate(@Param("id") Long id);
}
