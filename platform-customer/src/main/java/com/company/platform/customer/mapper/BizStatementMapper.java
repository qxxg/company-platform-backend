package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizStatement;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 客户对账单 Mapper。 */
@Mapper
public interface BizStatementMapper extends BaseMapper<BizStatement> {

    /**
     * 原子确认对账单。
     *
     * <p>只允许从 {@code GENERATED} 推进到 {@code CONFIRMED}，并发重复确认时
     * 第二次更新影响 0 行，由服务层返回冲突，避免覆盖首次确认的差异说明。</p>
     */
    @Update("""
            UPDATE biz_statement
            SET status = 'CONFIRMED',
                difference_note = #{note},
                confirmed_by = #{userId},
                confirmed_at = #{now},
                updated_by = #{userId},
                updated_at = #{now}
            WHERE id = #{id}
              AND status = 'GENERATED'
            """)
    int confirm(@Param("id") Long id,
                @Param("note") String note,
                @Param("userId") Long userId,
                @Param("now") LocalDateTime now);
}
