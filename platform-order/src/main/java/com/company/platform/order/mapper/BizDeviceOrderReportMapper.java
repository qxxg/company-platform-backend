package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizDeviceOrderReport;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单作业数据 Mapper。
 *
 * <p>单表（固定 ds_0）。按订单号唯一：上报时用条件更新覆盖已有行，返回 0 表示
 * 该订单还没有记录，需要插入。</p>
 */
@Mapper
public interface BizDeviceOrderReportMapper extends BaseMapper<BizDeviceOrderReport> {

    /** 覆盖已有订单的上报数据；没有命中返回 0。 */
    @Update("""
            UPDATE t_device_order_report
            SET device_no = #{deviceNo},
                work_order_no = #{workOrderNo},
                report_status = #{reportStatus},
                printed_length_meter = #{printedLengthMeter},
                progress_meter = #{progressMeter},
                progress_percent = #{progressPercent},
                ink_usage_ml = #{inkUsageMl},
                ink_delta_ml = #{inkDeltaMl},
                remaining_minutes = #{remainingMinutes},
                speed_meter_per_hour = #{speedMeterPerHour},
                width_meter = #{widthMeter},
                pass_count = #{passCount},
                device_state = #{deviceState},
                error_code = #{errorCode},
                image_uploaded = #{imageUploaded},
                reported_at = #{reportedAt},
                device_timestamp = #{deviceTimestamp},
                device_seq = #{deviceSeq},
                updated_at = #{updatedAt}
            WHERE order_no = #{orderNo}
            """)
    int overwrite(@Param("orderNo") Long orderNo,
                   @Param("deviceNo") String deviceNo,
                   @Param("workOrderNo") String workOrderNo,
                   @Param("reportStatus") String reportStatus,
                   @Param("printedLengthMeter") BigDecimal printedLengthMeter,
                   @Param("progressMeter") BigDecimal progressMeter,
                   @Param("progressPercent") BigDecimal progressPercent,
                   @Param("inkUsageMl") BigDecimal inkUsageMl,
                   @Param("inkDeltaMl") BigDecimal inkDeltaMl,
                   @Param("remainingMinutes") Integer remainingMinutes,
                   @Param("speedMeterPerHour") BigDecimal speedMeterPerHour,
                   @Param("widthMeter") BigDecimal widthMeter,
                   @Param("passCount") Integer passCount,
                   @Param("deviceState") String deviceState,
                   @Param("errorCode") String errorCode,
                   @Param("imageUploaded") Integer imageUploaded,
                   @Param("reportedAt") LocalDateTime reportedAt,
                   @Param("deviceTimestamp") Long deviceTimestamp,
                   @Param("deviceSeq") Long deviceSeq,
                   @Param("updatedAt") LocalDateTime updatedAt);

    /** 按订单号查询上报记录。 */
    @Select("SELECT * FROM t_device_order_report WHERE order_no = #{orderNo}")
    BizDeviceOrderReport selectByOrderNo(@Param("orderNo") Long orderNo);
}
