package com.company.platform.system.mapper;

import com.company.platform.system.entity.SysFactory;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工厂表 MyBatis-Flex Mapper。
 *
 * <p>工厂数据量小且变更频率低，当前直接查询全表；后续组织树页面可按
 * 状态、编码分页查询，并增加缓存。</p>
 */
@Mapper
public interface SysFactoryMapper extends BaseMapper<SysFactory> {
}
