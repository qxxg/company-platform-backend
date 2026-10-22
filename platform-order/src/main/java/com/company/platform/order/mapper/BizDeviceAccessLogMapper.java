package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizDeviceAccessLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备接入审计 Mapper。
 *
 * <p>单表（固定 ds_0）。只追加，不更新不删除。</p>
 */
@Mapper
public interface BizDeviceAccessLogMapper extends BaseMapper<BizDeviceAccessLog> {
}
