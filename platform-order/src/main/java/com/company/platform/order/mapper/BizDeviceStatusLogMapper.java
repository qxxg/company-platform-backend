package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizDeviceStatusLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备状态日志 Mapper。
 *
 * <p>只追加不修改，查询时按设备 ID 和时间倒序展示即可。</p>
 */
@Mapper
public interface BizDeviceStatusLogMapper extends BaseMapper<BizDeviceStatusLog> {
}
