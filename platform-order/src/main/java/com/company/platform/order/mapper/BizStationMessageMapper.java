package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizStationMessage;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 站内消息 Mapper。
 *
 * <p>标记已读时带 {@code user_id} 条件，防止用户通过猜 ID 修改他人的消息；
 * 已读时间只在首次阅读时写入。</p>
 */
@Mapper
public interface BizStationMessageMapper extends BaseMapper<BizStationMessage> {

    /** 将当前用户的一条消息标记为已读；重复调用不会覆盖首次阅读时间。 */
    @Update("""
            UPDATE t_station_message
            SET read_flag = 1, read_at = #{readAt}
            WHERE id = #{id} AND user_id = #{userId} AND read_flag = 0
            """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId,
                 @Param("readAt") LocalDateTime readAt);

    /** 将当前用户的全部未读消息标记为已读。 */
    @Update("""
            UPDATE t_station_message
            SET read_flag = 1, read_at = #{readAt}
            WHERE user_id = #{userId} AND read_flag = 0
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
