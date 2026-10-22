package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizDeviceReportStaging;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 未匹配暂存 Mapper。
 *
 * <p>单表（固定 ds_0）。订单号精确匹配成功后删除对应暂存记录。</p>
 */
@Mapper
public interface BizDeviceReportStagingMapper extends BaseMapper<BizDeviceReportStaging> {
}
