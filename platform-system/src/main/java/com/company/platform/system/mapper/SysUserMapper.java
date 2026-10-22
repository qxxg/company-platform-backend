package com.company.platform.system.mapper;

import com.company.platform.system.entity.SysUser;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 MyBatis-Flex Mapper。
 *
 * <p>目前直接使用 BaseMapper 的通用查询能力；账号新增、停用和重置密码
 * 将在系统管理模块中扩展为显式方法，避免控制器直接操作实体。</p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
