package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizDeviceCredential;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备接入凭据 Mapper。
 *
 * <p>单表（固定 ds_0）。报文验签时按设备编号读取一机一密密钥。</p>
 */
@Mapper
public interface BizDeviceCredentialMapper extends BaseMapper<BizDeviceCredential> {

    /** 按设备编号查询凭据。 */
    @Select("SELECT * FROM t_device_credential WHERE device_no = #{deviceNo}")
    BizDeviceCredential selectByDeviceNo(@Param("deviceNo") String deviceNo);
}
