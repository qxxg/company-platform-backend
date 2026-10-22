package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizDeviceImage;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单图片 Mapper。
 *
 * <p>单表（固定 ds_0）。按订单号唯一，用于保证“一条订单只上传一次图片”。</p>
 */
@Mapper
public interface BizDeviceImageMapper extends BaseMapper<BizDeviceImage> {

    /** 按订单号查询图片记录。 */
    @Select("SELECT * FROM t_device_image WHERE order_no = #{orderNo}")
    BizDeviceImage selectByOrderNo(@Param("orderNo") Long orderNo);
}
