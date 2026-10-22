package com.company.platform.approval.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.approval.entity.ApprovalDefinition;
import com.company.platform.approval.mapper.ApprovalDefinitionMapper;
import com.company.platform.approval.web.ApprovalDefinitionCommand;
import com.company.platform.approval.web.ApprovalDefinitionView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 审批定义维护服务。
 *
 * <p>审批档位由财务在系统上线前配置：同一业务类型可以配置多档，按金额区间
 * 匹配，优先级数字小的先命中。定义只允许停用，不做物理删除，避免历史审批单
 * 失去依据。</p>
 */
@Service
public class ApprovalDefinitionService {

    private final ApprovalDefinitionMapper definitionMapper;

    public ApprovalDefinitionService(ApprovalDefinitionMapper definitionMapper) {
        this.definitionMapper = definitionMapper;
    }

    /**
     * 审批定义分页。
     *
     * @param pageNumber  页码
     * @param pageSize    每页条数
     * @param businessType 业务类型过滤
     * @param status      状态过滤
     */
    @Transactional(readOnly = true)
    public List<ApprovalDefinitionView> page(long pageNumber, long pageSize, String businessType, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (businessType != null && !businessType.isBlank()) {
            conditions.add("business_type = ?");
            arguments.add(businessType.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("business_type", true).orderBy("priority", true);
        Page<ApprovalDefinition> page = definitionMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<ApprovalDefinitionView> views = new ArrayList<>(page.getRecords().size());
        for (ApprovalDefinition definition : page.getRecords()) {
            views.add(ApprovalDefinitionView.from(definition));
        }
        return views;
    }

    /** 审批定义总数（按当前筛选条件）。 */
    @Transactional(readOnly = true)
    public long count(String businessType, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (businessType != null && !businessType.isBlank()) {
            conditions.add("business_type = ?");
            arguments.add(businessType.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        return definitionMapper.selectCountByQuery(query);
    }

    /** 新建审批定义。 */
    @Transactional
    public ApprovalDefinitionView create(ApprovalDefinitionCommand command) {
        validate(command);
        String name = command.name().trim();
        if (definitionMapper.selectOneByQuery(QueryWrapper.create()
                .where("business_type = ? AND name = ?", command.businessType().trim().toUpperCase(Locale.ROOT), name)) != null) {
            throw new BizException(409, "同一业务类型下已存在同名审批流程：" + name);
        }
        LocalDateTime now = LocalDateTime.now();
        ApprovalDefinition definition = new ApprovalDefinition();
        apply(definition, command);
        definition.setStatus("ACTIVE");
        definition.setCreatedBy(SecurityUtils.userId());
        definition.setCreatedAt(now);
        definition.setUpdatedBy(SecurityUtils.userId());
        definition.setUpdatedAt(now);
        definition.setVersion(0);
        definitionMapper.insert(definition);
        return ApprovalDefinitionView.from(definition);
    }

    /** 修改审批定义；金额档位与审批人可以调整。 */
    @Transactional
    public ApprovalDefinitionView update(long id, ApprovalDefinitionCommand command) {
        validate(command);
        ApprovalDefinition definition = definitionMapper.selectOneById(id);
        if (definition == null) {
            throw new BizException(404, "审批定义不存在：" + id);
        }
        apply(definition, command);
        definition.setUpdatedBy(SecurityUtils.userId());
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.update(definition);
        return ApprovalDefinitionView.from(definition);
    }

    /** 启用/停用审批定义。 */
    @Transactional
    public ApprovalDefinitionView changeStatus(long id, String targetStatus) {
        ApprovalDefinition definition = definitionMapper.selectOneById(id);
        if (definition == null) {
            throw new BizException(404, "审批定义不存在：" + id);
        }
        String target = targetStatus == null ? "" : targetStatus.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(target) && !"DISABLED".equals(target)) {
            throw new BizException(400, "审批定义状态只能是启用或停用");
        }
        definition.setStatus(target);
        definition.setUpdatedBy(SecurityUtils.userId());
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.update(definition);
        return ApprovalDefinitionView.from(definition);
    }

    /** 校验档位区间与审批人配置。 */
    private void validate(ApprovalDefinitionCommand command) {
        if (command.amountMax() != null && command.amountMax() <= command.amountMin()) {
            throw new BizException(400, "金额上限必须大于金额下限");
        }
        if ((command.approverRole() == null || command.approverRole().isBlank())
                && command.approverUserId() == null) {
            throw new BizException(400, "审批角色和指定审批人至少填写一项");
        }
    }

    /** 写入定义字段。 */
    private void apply(ApprovalDefinition definition, ApprovalDefinitionCommand command) {
        definition.setBusinessType(command.businessType().trim().toUpperCase(Locale.ROOT));
        definition.setName(command.name().trim());
        definition.setAmountMin(command.amountMin());
        definition.setAmountMax(command.amountMax());
        definition.setApproverRole(command.approverRole() == null || command.approverRole().isBlank()
                ? null : command.approverRole().trim().toUpperCase(Locale.ROOT));
        definition.setApproverUserId(command.approverUserId());
        definition.setPriority(command.priority());
        definition.setRemark(command.remark() == null || command.remark().isBlank()
                ? null : command.remark().trim());
    }
}
