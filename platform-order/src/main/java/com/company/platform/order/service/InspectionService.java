package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizInspectionRecord;
import com.company.platform.order.mapper.BizDeviceMapper;
import com.company.platform.order.mapper.BizInspectionRecordMapper;
import com.company.platform.order.web.InspectionCreateCommand;
import com.company.platform.order.web.InspectionPageView;
import com.company.platform.order.web.InspectionSummaryView;
import com.company.platform.order.web.InspectionView;
import com.company.platform.order.web.MaintenanceCreateCommand;
import com.company.platform.order.web.MaintenanceView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 设备点检保养记录服务。
 *
 * <p>操作员按日/周/月登记点检结果；结果异常时可以一键生成维护工单，维护工单
 * 通过 {@code inspectionId} 回溯到点检记录，避免重复描述问题。</p>
 *
 * <p>点检记录只读开放给销售（设备维度信息，用于交期判断），写入仅限具备
 * 维护写入权限的角色。</p>
 */
@Service
public class InspectionService {

    private static final Set<String> PLAN_TYPES = Set.of("DAILY", "WEEKLY", "MONTHLY");
    private static final Set<String> RESULTS = Set.of("NORMAL", "ABNORMAL");

    private final BizInspectionRecordMapper inspectionMapper;
    private final BizDeviceMapper deviceMapper;
    private final MaintenanceService maintenanceService;

    public InspectionService(BizInspectionRecordMapper inspectionMapper,
                             BizDeviceMapper deviceMapper,
                             MaintenanceService maintenanceService) {
        this.inspectionMapper = inspectionMapper;
        this.deviceMapper = deviceMapper;
        this.maintenanceService = maintenanceService;
    }

    /**
     * 点检记录分页。
     *
     * @param pageNumber 页码
     * @param pageSize   每页条数
     * @param keyword    设备编号/点检内容关键字
     * @param deviceId   设备过滤
     * @param factoryId  工厂过滤
     * @param result     结果过滤：NORMAL/ABNORMAL
     * @param planType   周期过滤：DAILY/WEEKLY/MONTHLY
     */
    @Transactional(readOnly = true)
    public InspectionPageView page(long pageNumber, long pageSize, String keyword, Long deviceId,
                                   Long factoryId, String result, String planType) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        appendFilter(conditions, arguments, keyword, deviceId, factoryId, result, planType);
        appendFactoryScope(conditions, arguments);

        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("inspection_date", false).orderBy("id", false);
        Page<BizInspectionRecord> page = inspectionMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<InspectionView> records = buildViews(page.getRecords());

        QueryWrapper summaryQuery = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            summaryQuery.where(String.join(" AND ", conditions), arguments.toArray());
        }
        List<BizInspectionRecord> all = inspectionMapper.selectListByQuery(summaryQuery);
        long normal = 0;
        long abnormal = 0;
        Set<Long> deviceIds = new LinkedHashSet<>();
        for (BizInspectionRecord record : all) {
            if ("ABNORMAL".equals(record.getResult())) {
                abnormal++;
            } else {
                normal++;
            }
            deviceIds.add(record.getDeviceId());
        }
        InspectionSummaryView summary =
                new InspectionSummaryView(all.size(), normal, abnormal, deviceIds.size());
        return new InspectionPageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(), summary);
    }

    /**
     * 登记点检记录。
     *
     * <p>结果为异常时至少要有一个异常项；异常项数不能超过点检项数。工厂强制
     * 取设备归属工厂。</p>
     */
    @Transactional
    public InspectionView create(InspectionCreateCommand command) {
        BizDevice device = deviceMapper.selectOneById(command.deviceId());
        if (device == null) {
            throw new BizException(404, "设备不存在：" + command.deviceId());
        }
        maintenanceService.requireFactoryWriteAccess(device.getFactoryId());
        String planType = command.planType().trim().toUpperCase(Locale.ROOT);
        if (!PLAN_TYPES.contains(planType)) {
            throw new BizException(400, "点检周期只能是日常点检、周点检或月度保养检查");
        }
        String result = command.result().trim().toUpperCase(Locale.ROOT);
        if (!RESULTS.contains(result)) {
            throw new BizException(400, "点检结果只能是正常或异常");
        }
        int itemCount = command.itemCount() == null ? 0 : command.itemCount();
        int abnormalCount = command.abnormalCount() == null ? 0 : command.abnormalCount();
        if (abnormalCount > itemCount) {
            throw new BizException(400, "异常项数不能超过点检项数");
        }
        if ("ABNORMAL".equals(result) && abnormalCount == 0) {
            throw new BizException(400, "结果为异常时必须填写异常项数");
        }
        if (command.inspectionDate() == null || command.inspectionDate().isAfter(java.time.LocalDate.now())) {
            throw new BizException(400, "点检日期不能为空或晚于今天");
        }

        LocalDateTime now = LocalDateTime.now();
        BizInspectionRecord record = new BizInspectionRecord();
        record.setDeviceId(device.getId());
        record.setDeviceNo(device.getDeviceNo());
        record.setFactoryId(device.getFactoryId());
        record.setPlanType(planType);
        record.setInspectionDate(command.inspectionDate());
        record.setResult(result);
        record.setItemCount(itemCount);
        record.setAbnormalCount(abnormalCount);
        record.setContent(trimToNull(command.content()));
        record.setHandlerUserId(command.handlerUserId() == null ? SecurityUtils.userId() : command.handlerUserId());
        record.setHandlerName(handlerName(command.handlerUserId()));
        record.setCreatedBy(SecurityUtils.userId());
        record.setCreatedAt(now);
        record.setUpdatedBy(SecurityUtils.userId());
        record.setUpdatedAt(now);
        record.setVersion(0);
        inspectionMapper.insert(record);
        return InspectionView.from(record, device.getDeviceName());
    }

    /**
     * 由异常点检生成维护工单。
     *
     * <p>生成的工单类型为“点检整改”，默认高优先级；维护工单保存点检记录
     * ID，便于回溯。正常点检不允许生成工单。</p>
     */
    @Transactional
    public MaintenanceView createMaintenanceFromInspection(long inspectionId) {
        BizInspectionRecord record = inspectionMapper.selectOneById(inspectionId);
        if (record == null) {
            throw new BizException(404, "点检记录不存在：" + inspectionId);
        }
        if (!"ABNORMAL".equals(record.getResult())) {
            throw new BizException(409, "只有结果为异常的点检才能生成维护工单");
        }
        String content = record.getContent() == null ? "点检发现异常，需安排整改" : record.getContent();
        String title = "点检异常整改：" + record.getDeviceNo();
        MaintenanceCreateCommand command = new MaintenanceCreateCommand(record.getDeviceId(),
                MaintenanceService.TYPE_INSPECTION, title, content, "HIGH",
                null, null, record.getHandlerUserId(), record.getId(), null);
        return maintenanceService.create(command);
    }

    /** 追加筛选条件；参数全部使用占位符。 */
    private void appendFilter(List<String> conditions, List<Object> arguments, String keyword,
                              Long deviceId, Long factoryId, String result, String planType) {
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(device_no LIKE ? OR content LIKE ? OR handler_name LIKE ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (deviceId != null) {
            conditions.add("device_id = ?");
            arguments.add(deviceId);
        }
        if (factoryId != null) {
            conditions.add("factory_id = ?");
            arguments.add(factoryId);
        }
        if (result != null && !result.isBlank()) {
            conditions.add("result = ?");
            arguments.add(result.trim().toUpperCase(Locale.ROOT));
        }
        if (planType != null && !planType.isBlank()) {
            conditions.add("plan_type = ?");
            arguments.add(planType.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** 追加工厂数据范围：仅厂长受限。 */
    private void appendFactoryScope(List<String> conditions, List<Object> arguments) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            Long factoryId = SecurityUtils.factoryId();
            conditions.add("factory_id = ?");
            arguments.add(factoryId == null ? -1L : factoryId);
        }
    }

    /** 批量构建视图，顺带补齐设备名称。 */
    private List<InspectionView> buildViews(List<BizInspectionRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> deviceIds = new LinkedHashSet<>();
        for (BizInspectionRecord record : records) {
            deviceIds.add(record.getDeviceId());
        }
        Map<Long, BizDevice> devices = new java.util.HashMap<>();
        if (!deviceIds.isEmpty()) {
            List<BizDevice> list = deviceMapper.selectListByQuery(QueryWrapper.create()
                    .where("id IN (" + placeholders(deviceIds.size()) + ")", deviceIds.toArray()));
            for (BizDevice device : list) {
                devices.put(device.getId(), device);
            }
        }
        List<InspectionView> views = new ArrayList<>(records.size());
        for (BizInspectionRecord record : records) {
            BizDevice device = devices.get(record.getDeviceId());
            views.add(InspectionView.from(record, device == null ? null : device.getDeviceName()));
        }
        return views;
    }

    /** 点检人姓名：指定点检人时使用当前用户昵称兜底。 */
    private static String handlerName(Long handlerUserId) {
        if (handlerUserId == null || handlerUserId.equals(SecurityUtils.userId())) {
            return SecurityUtils.nickname();
        }
        return "用户#" + handlerUserId;
    }

    /** 生成 IN 子句占位符。 */
    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    /** 去首尾空格，空字符串转 null。 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
