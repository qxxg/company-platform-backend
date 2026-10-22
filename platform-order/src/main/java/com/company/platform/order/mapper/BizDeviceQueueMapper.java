package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizDeviceQueue;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备队列 Mapper。
 *
 * <p>插队审批需要在事务中锁定整台设备的活跃队列，再统一重排序号和计划时间。
 * 查询按 {@code queue_seq, id} 排序，序号相同时以主键兜底，保证 FIFO 稳定。</p>
 */
@Mapper
public interface BizDeviceQueueMapper extends BaseMapper<BizDeviceQueue> {

    /** 锁定一台设备的活跃队列（等待中/生产中），防止并发插队造成序号冲突。 */
    @Select("""
            SELECT * FROM t_device_queue
            WHERE device_id = #{deviceId} AND status IN ('WAITING','RUNNING')
            ORDER BY queue_seq, id
            FOR UPDATE
            """)
    List<BizDeviceQueue> selectActiveForUpdate(@Param("deviceId") Long deviceId);

    /** 查询一台设备的活跃队列，用于看板和计划时间重算。 */
    @Select("""
            SELECT * FROM t_device_queue
            WHERE device_id = #{deviceId} AND status IN ('WAITING','RUNNING')
            ORDER BY queue_seq, id
            """)
    List<BizDeviceQueue> selectActiveByDevice(@Param("deviceId") Long deviceId);

    /** 查询一批设备的活跃队列，批量构建排产看板，避免逐台设备查询。 */
    @Select("""
            <script>
            SELECT * FROM t_device_queue
            WHERE status IN ('WAITING','RUNNING')
              AND device_id IN
              <foreach collection="deviceIds" item="deviceId" open="(" separator="," close=")">
                #{deviceId}
              </foreach>
            ORDER BY device_id, queue_seq, id
            </script>
            """)
    List<BizDeviceQueue> selectActiveByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    /** 当前设备最大队列序号；没有记录时返回 null。 */
    @Select("SELECT MAX(queue_seq) FROM t_device_queue WHERE device_id = #{deviceId}")
    Long selectMaxSeq(@Param("deviceId") Long deviceId);

    /** 更新队列序号和预计时间；只更新仍处于 WAITING 的记录。 */
    @Update("""
            UPDATE t_device_queue
            SET queue_seq = #{queueSeq}, planned_start = #{plannedStart}, planned_end = #{plannedEnd},
                updated_at = #{updatedAt}
            WHERE id = #{id} AND status = 'WAITING'
            """)
    int updatePlan(@Param("id") Long id, @Param("queueSeq") Long queueSeq,
                   @Param("plannedStart") LocalDateTime plannedStart,
                   @Param("plannedEnd") LocalDateTime plannedEnd,
                   @Param("updatedAt") LocalDateTime updatedAt);

    /** 更新队列状态。 */
    @Update("""
            UPDATE t_device_queue
            SET status = #{status}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 只更新队列序号，用于插队重排。
     *
     * <p>生产中记录也要更新序号，保证队列第一条始终是当前生产工单；仅更新
     * WAITING 的 {@link #updatePlan} 无法覆盖这种情况。</p>
     */
    @Update("""
            UPDATE t_device_queue
            SET queue_seq = #{queueSeq}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateSeq(@Param("id") Long id, @Param("queueSeq") Long queueSeq,
                  @Param("updatedAt") LocalDateTime updatedAt);

    /** 记录插队结果：原序号、申请 ID、原因一起留痕。 */
    @Update("""
            UPDATE t_device_queue
            SET original_seq = #{originalSeq}, jump_request_id = #{jumpRequestId},
                jump_reason = #{jumpReason}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int markJump(@Param("id") Long id, @Param("originalSeq") Long originalSeq,
                 @Param("jumpRequestId") Long jumpRequestId, @Param("jumpReason") String jumpReason,
                 @Param("updatedAt") LocalDateTime updatedAt);
}
