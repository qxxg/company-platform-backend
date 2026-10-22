package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizStockTransfer;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 跨厂调拨单 Mapper。
 *
 * <p>单表（固定 ds_0）。出库与入库都会先锁单据行，保证一张调拨单不会被重复
 * 出库或重复入库。</p>
 */
@Mapper
public interface BizStockTransferMapper extends BaseMapper<BizStockTransfer> {

    /** 在事务中锁定调拨单行。 */
    @Select("SELECT * FROM t_stock_transfer WHERE id = #{id} FOR UPDATE")
    BizStockTransfer selectForUpdate(@Param("id") Long id);
}
