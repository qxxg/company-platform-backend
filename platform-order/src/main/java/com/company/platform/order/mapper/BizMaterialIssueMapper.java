package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizMaterialIssue;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工单领料单 Mapper。
 *
 * <p>单表（固定 ds_0）。确认发料会先锁单据行，保证同一张领料单不会被重复发料导致
 * 库存重复扣减。</p>
 */
@Mapper
public interface BizMaterialIssueMapper extends BaseMapper<BizMaterialIssue> {

    /** 在事务中锁定领料单行。 */
    @Select("SELECT * FROM t_material_issue WHERE id = #{id} FOR UPDATE")
    BizMaterialIssue selectForUpdate(@Param("id") Long id);
}
