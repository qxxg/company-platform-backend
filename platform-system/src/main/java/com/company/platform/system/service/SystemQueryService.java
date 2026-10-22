package com.company.platform.system.service;

import com.company.platform.system.entity.SysFactory;
import com.company.platform.system.entity.SysUser;
import com.company.platform.system.mapper.SysFactoryMapper;
import com.company.platform.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统基础数据只读服务。
 *
 * <p>控制器只处理协议和权限，数据访问集中在这里，便于以后增加分页、数据范围
 * （总部可看全部、工厂账号只看本厂）以及缓存。</p>
 */
@Service
public class SystemQueryService {

    private final SysUserMapper sysUserMapper;
    private final SysFactoryMapper sysFactoryMapper;

    public SystemQueryService(SysUserMapper sysUserMapper, SysFactoryMapper sysFactoryMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysFactoryMapper = sysFactoryMapper;
    }

    /**
     * 查询全部用户。当前数据量很小，直接全表查询；
     * 员工规模扩大后应改为分页并增加工厂/状态筛选。
     */
    @Transactional(readOnly = true)
    public List<SysUser> listUsers() {
        return sysUserMapper.selectAll();
    }

    /** 查询全部工厂。工厂是组织树叶子节点，数量有限。 */
    @Transactional(readOnly = true)
    public List<SysFactory> listFactories() {
        return sysFactoryMapper.selectAll();
    }
}
