package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizWarehouse;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 仓库档案 Mapper。
 *
 * <p>仓库是单表（固定 ds_0）。停用仓库、创建主仓等操作需要先锁定仓库行，避免
 * 并发下出现同工厂两个主仓或停用仍有库存的仓库。</p>
 */
@Mapper
public interface BizWarehouseMapper extends BaseMapper<BizWarehouse> {

    /** 在事务中锁定仓库行。 */
    @Select("SELECT * FROM t_warehouse WHERE id = #{id} FOR UPDATE")
    BizWarehouse selectForUpdate(@Param("id") Long id);
}
