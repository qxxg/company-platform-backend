package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizInventory;
import com.company.platform.order.entity.BizMaterial;
import com.company.platform.order.entity.BizWarehouse;
import com.company.platform.order.mapper.BizInventoryMapper;
import com.company.platform.order.mapper.BizMaterialMapper;
import com.company.platform.order.mapper.BizWarehouseMapper;
import com.company.platform.order.web.InventoryView;
import com.company.platform.order.web.MaterialCreateCommand;
import com.company.platform.order.web.MaterialUpdateCommand;
import com.company.platform.order.web.MaterialView;
import com.company.platform.order.web.WarehouseCreateCommand;
import com.company.platform.order.web.WarehouseUpdateCommand;
import com.company.platform.order.web.WarehouseView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 仓库、物料档案与库存查询服务。
 *
 * <p><strong>仓储口径：</strong>每个工厂至少有一个主仓；仓库不跨厂，跨厂移库
 * 走调拨单。物料单价最多 4 位小数、损耗率 2 位小数，数量类字段统一保留 2 位
 * 小数并截断。</p>
 *
 * <p><strong>库存口径：</strong>库存只能由采购入库和调拨入库增加，系统不提供
 * 库存调整入口，因此没有期初库存；查询接口只读，出入库在采购入库和调拨服务
 * 中完成。</p>
 *
 * <p><strong>数据范围：</strong>厂长只能维护本厂仓库和物料，管理员可跨厂；
 * 财务与操作员只读。</p>
 */
@Service
public class WarehouseService {

    /** 仓库类型：主仓。 */
    public static final String WAREHOUSE_MAIN = "MAIN";
    /** 仓库状态：启用。 */
    public static final String WAREHOUSE_ACTIVE = "ACTIVE";

    private static final Set<String> WAREHOUSE_TYPES = Set.of("MAIN", "FEEDING", "RETURN");
    private static final Set<String> MATERIAL_TYPES = Set.of("FABRIC", "CHEMICAL", "ACCESSORY");

    private final BizWarehouseMapper warehouseMapper;
    private final BizMaterialMapper materialMapper;
    private final BizInventoryMapper inventoryMapper;

    public WarehouseService(BizWarehouseMapper warehouseMapper,
                            BizMaterialMapper materialMapper,
                            BizInventoryMapper inventoryMapper) {
        this.warehouseMapper = warehouseMapper;
        this.materialMapper = materialMapper;
        this.inventoryMapper = inventoryMapper;
    }

    // ------------------------------------------------------------------ 仓库

    /**
     * 仓库分页，附带库存概览。
     *
     * @param pageNumber 页码
     * @param pageSize   每页条数
     * @param keyword    仓库编号/名称关键字
     * @param factoryId  工厂过滤
     * @param status     状态过滤
     */
    @Transactional(readOnly = true)
    public List<WarehouseView> pageWarehouses(long pageNumber, long pageSize, String keyword,
                                              Long factoryId, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(warehouse_no LIKE ? OR warehouse_name LIKE ?)");
            arguments.add(like);
            arguments.add(like);
        }
        Long effectiveFactory = effectiveFactory(factoryId);
        if (effectiveFactory != null) {
            conditions.add("factory_id = ?");
            arguments.add(effectiveFactory);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("factory_id", true).orderBy("warehouse_no", true);
        Page<BizWarehouse> page = warehouseMapper.paginate(Page.of(pageNumber, pageSize), query);
        return buildWarehouseViews(page.getRecords());
    }

    /** 仓库总数（按当前数据范围）。 */
    @Transactional(readOnly = true)
    public long countWarehouses(Long factoryId, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        Long effectiveFactory = effectiveFactory(factoryId);
        if (effectiveFactory != null) {
            conditions.add("factory_id = ?");
            arguments.add(effectiveFactory);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        return warehouseMapper.selectCountByQuery(query);
    }

    /** 仓库下拉选项：按工厂过滤，便于单据选择收货仓库。 */
    @Transactional(readOnly = true)
    public List<WarehouseView> listWarehouses(Long factoryId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        Long effectiveFactory = effectiveFactory(factoryId);
        if (effectiveFactory != null) {
            conditions.add("factory_id = ?");
            arguments.add(effectiveFactory);
        }
        conditions.add("status = 'ACTIVE'");
        QueryWrapper query = QueryWrapper.create()
                .where(String.join(" AND ", conditions), arguments.toArray())
                .orderBy("factory_id", true).orderBy("warehouse_no", true);
        return buildWarehouseViews(warehouseMapper.selectListByQuery(query));
    }

    /** 仓库详情。 */
    @Transactional(readOnly = true)
    public WarehouseView getWarehouse(long id) {
        return buildWarehouseViews(List.of(requireWarehouse(id))).get(0);
    }

    /** 新建仓库。 */
    @Transactional
    public WarehouseView createWarehouse(WarehouseCreateCommand command) {
        requireFactoryWrite(command.factoryId());
        String type = command.warehouseType().trim().toUpperCase(Locale.ROOT);
        if (!WAREHOUSE_TYPES.contains(type)) {
            throw new BizException(400, "仓库类型只能是主仓、备料仓或退货仓");
        }
        String no = command.warehouseNo().trim().toUpperCase(Locale.ROOT);
        if (warehouseMapper.selectOneByQuery(QueryWrapper.create()
                .where("warehouse_no = ?", no)) != null) {
            throw new BizException(409, "仓库编号已存在：" + no);
        }
        if (WAREHOUSE_MAIN.equals(type)) {
            assertSingleMainWarehouse(command.factoryId(), null);
        }
        LocalDateTime now = LocalDateTime.now();
        BizWarehouse warehouse = new BizWarehouse();
        warehouse.setWarehouseNo(no);
        warehouse.setWarehouseName(command.warehouseName().trim());
        warehouse.setFactoryId(command.factoryId());
        warehouse.setWarehouseType(type);
        warehouse.setAddress(trimToNull(command.address()));
        warehouse.setManagerUserId(command.managerUserId());
        warehouse.setStatus(WAREHOUSE_ACTIVE);
        warehouse.setRemark(trimToNull(command.remark()));
        warehouse.setCreatedBy(SecurityUtils.userId());
        warehouse.setCreatedAt(now);
        warehouse.setUpdatedBy(SecurityUtils.userId());
        warehouse.setUpdatedAt(now);
        warehouse.setVersion(0);
        warehouseMapper.insert(warehouse);
        return buildWarehouseViews(List.of(warehouse)).get(0);
    }

    /** 修改仓库；编号和所属工厂不可修改。 */
    @Transactional
    public WarehouseView updateWarehouse(long id, WarehouseUpdateCommand command) {
        BizWarehouse warehouse = requireWarehouse(id);
        requireFactoryWrite(warehouse.getFactoryId());
        String type = command.warehouseType().trim().toUpperCase(Locale.ROOT);
        if (!WAREHOUSE_TYPES.contains(type)) {
            throw new BizException(400, "仓库类型只能是主仓、备料仓或退货仓");
        }
        if (WAREHOUSE_MAIN.equals(type) && !WAREHOUSE_MAIN.equals(warehouse.getWarehouseType())) {
            assertSingleMainWarehouse(warehouse.getFactoryId(), warehouse.getId());
        }
        warehouse.setWarehouseName(command.warehouseName().trim());
        warehouse.setWarehouseType(type);
        warehouse.setAddress(trimToNull(command.address()));
        warehouse.setManagerUserId(command.managerUserId());
        warehouse.setRemark(trimToNull(command.remark()));
        warehouse.setUpdatedBy(SecurityUtils.userId());
        warehouse.setUpdatedAt(LocalDateTime.now());
        warehouseMapper.update(warehouse);
        return buildWarehouseViews(List.of(warehouse)).get(0);
    }

    /**
     * 启用/停用仓库。
     *
     * <p>停用前必须清空库存；工厂唯一的启用主仓不允许停用，避免出现没有主仓
     * 的工厂。</p>
     */
    @Transactional
    public WarehouseView changeWarehouseStatus(long id, String targetStatus) {
        BizWarehouse warehouse = warehouseMapper.selectForUpdate(id);
        if (warehouse == null) {
            throw new BizException(404, "仓库不存在：" + id);
        }
        requireFactoryWrite(warehouse.getFactoryId());
        String target = targetStatus == null ? "" : targetStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "DISABLED").contains(target)) {
            throw new BizException(400, "仓库状态只能是启用或停用");
        }
        if (target.equals(warehouse.getStatus())) {
            return buildWarehouseViews(List.of(warehouse)).get(0);
        }
        if ("DISABLED".equals(target)) {
            BigDecimal quantity = totalQuantity(id);
            if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                throw new BizException(409, "仓库仍有库存，无法停用：" + quantity.toPlainString());
            }
            if (WAREHOUSE_MAIN.equals(warehouse.getWarehouseType())) {
                assertAnotherMainWarehouse(warehouse.getFactoryId(), warehouse.getId());
            }
        }
        warehouse.setStatus(target);
        warehouse.setUpdatedBy(SecurityUtils.userId());
        warehouse.setUpdatedAt(LocalDateTime.now());
        warehouseMapper.update(warehouse);
        return buildWarehouseViews(List.of(warehouse)).get(0);
    }

    // ------------------------------------------------------------------ 物料

    /** 物料分页，附带全部仓库库存合计。 */
    @Transactional(readOnly = true)
    public List<MaterialView> pageMaterials(long pageNumber, long pageSize, String keyword,
                                            String materialType, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(material_code LIKE ? OR material_name LIKE ? OR spec LIKE ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (materialType != null && !materialType.isBlank()) {
            conditions.add("material_type = ?");
            arguments.add(materialType.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("material_code", true);
        Page<BizMaterial> page = materialMapper.paginate(Page.of(pageNumber, pageSize), query);
        return buildMaterialViews(page.getRecords());
    }

    /** 物料总数（按当前筛选条件）。 */
    @Transactional(readOnly = true)
    public long countMaterials(String keyword, String materialType, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(material_code LIKE ? OR material_name LIKE ? OR spec LIKE ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (materialType != null && !materialType.isBlank()) {
            conditions.add("material_type = ?");
            arguments.add(materialType.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        return materialMapper.selectCountByQuery(query);
    }

    /** 物料下拉选项：只返回启用物料。 */
    @Transactional(readOnly = true)
    public List<MaterialView> listMaterials() {
        return buildMaterialViews(materialMapper.selectListByQuery(QueryWrapper.create()
                .where("status = 'ACTIVE'")
                .orderBy("material_code", true)));
    }

    /** 物料详情。 */
    @Transactional(readOnly = true)
    public MaterialView getMaterial(long id) {
        return buildMaterialViews(List.of(requireMaterial(id))).get(0);
    }

    /** 新建物料档案。 */
    @Transactional
    public MaterialView createMaterial(MaterialCreateCommand command) {
        String type = command.materialType().trim().toUpperCase(Locale.ROOT);
        if (!MATERIAL_TYPES.contains(type)) {
            throw new BizException(400, "物料类型只能是布料、化工或辅料");
        }
        String code = command.materialCode().trim().toUpperCase(Locale.ROOT);
        if (materialMapper.selectOneByQuery(QueryWrapper.create()
                .where("material_code = ?", code)) != null) {
            throw new BizException(409, "物料编码已存在：" + code);
        }
        LocalDateTime now = LocalDateTime.now();
        BizMaterial material = new BizMaterial();
        material.setMaterialCode(code);
        material.setMaterialName(command.materialName().trim());
        material.setMaterialType(type);
        material.setSpec(trimToNull(command.spec()));
        material.setUnit(command.unit().trim());
        material.setUnitPrice(normalizePrice(command.unitPrice()));
        material.setLossRate(normalizeLossRate(command.lossRate()));
        material.setSafetyStock(normalizeQuantity(command.safetyStock()));
        material.setStatus("ACTIVE");
        material.setRemark(trimToNull(command.remark()));
        material.setCreatedBy(SecurityUtils.userId());
        material.setCreatedAt(now);
        material.setUpdatedBy(SecurityUtils.userId());
        material.setUpdatedAt(now);
        material.setVersion(0);
        materialMapper.insert(material);
        return buildMaterialViews(List.of(material)).get(0);
    }

    /** 修改物料档案；物料编码不可修改。 */
    @Transactional
    public MaterialView updateMaterial(long id, MaterialUpdateCommand command) {
        BizMaterial material = requireMaterial(id);
        String type = command.materialType().trim().toUpperCase(Locale.ROOT);
        if (!MATERIAL_TYPES.contains(type)) {
            throw new BizException(400, "物料类型只能是布料、化工或辅料");
        }
        material.setMaterialName(command.materialName().trim());
        material.setMaterialType(type);
        material.setSpec(trimToNull(command.spec()));
        material.setUnit(command.unit().trim());
        material.setUnitPrice(normalizePrice(command.unitPrice()));
        material.setLossRate(normalizeLossRate(command.lossRate()));
        material.setSafetyStock(normalizeQuantity(command.safetyStock()));
        material.setRemark(trimToNull(command.remark()));
        material.setUpdatedBy(SecurityUtils.userId());
        material.setUpdatedAt(LocalDateTime.now());
        materialMapper.update(material);
        return buildMaterialViews(List.of(material)).get(0);
    }

    /** 启用/停用物料；已存在库存的物料不允许停用。 */
    @Transactional
    public MaterialView changeMaterialStatus(long id, String targetStatus) {
        BizMaterial material = requireMaterial(id);
        String target = targetStatus == null ? "" : targetStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "DISABLED").contains(target)) {
            throw new BizException(400, "物料状态只能是启用或停用");
        }
        if (target.equals(material.getStatus())) {
            return buildMaterialViews(List.of(material)).get(0);
        }
        if ("DISABLED".equals(target)) {
            BigDecimal quantity = materialQuantity(material.getId());
            if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                throw new BizException(409, "物料仍有库存，无法停用：" + quantity.toPlainString());
            }
        }
        material.setStatus(target);
        material.setUpdatedBy(SecurityUtils.userId());
        material.setUpdatedAt(LocalDateTime.now());
        materialMapper.update(material);
        return buildMaterialViews(List.of(material)).get(0);
    }

    // ------------------------------------------------------------------ 库存

    /**
     * 库存分页。
     *
     * @param warehouseId 仓库过滤
     * @param factoryId   工厂过滤
     * @param materialId  物料过滤
     * @param keyword     物料编码/名称关键字
     * @param belowSafety 只看低于安全库存
     */
    @Transactional(readOnly = true)
    public List<InventoryView> pageInventory(long pageNumber, long pageSize, Long warehouseId,
                                             Long factoryId, Long materialId, String keyword,
                                             boolean belowSafety) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        Long effectiveFactory = effectiveFactory(factoryId);
        if (effectiveFactory != null) {
            conditions.add("factory_id = ?");
            arguments.add(effectiveFactory);
        }
        if (warehouseId != null) {
            conditions.add("warehouse_id = ?");
            arguments.add(warehouseId);
        }
        if (materialId != null) {
            conditions.add("material_id = ?");
            arguments.add(materialId);
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("factory_id", true).orderBy("warehouse_id", true).orderBy("material_id", true);
        Page<BizInventory> page = inventoryMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<BizInventory> records = page.getRecords();
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, BizWarehouse> warehouses = loadWarehouses(records.stream()
                .map(BizInventory::getWarehouseId).distinct().toList());
        Map<Long, BizMaterial> materials = loadMaterials(records.stream()
                .map(BizInventory::getMaterialId).distinct().toList());
        List<InventoryView> views = new ArrayList<>(records.size());
        for (BizInventory inventory : records) {
            BizWarehouse warehouse = warehouses.get(inventory.getWarehouseId());
            BizMaterial material = materials.get(inventory.getMaterialId());
            if (material == null) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = keyword.trim().toLowerCase(Locale.ROOT);
                boolean matched = material.getMaterialCode().toLowerCase(Locale.ROOT).contains(like)
                        || material.getMaterialName().toLowerCase(Locale.ROOT).contains(like);
                if (!matched) {
                    continue;
                }
            }
            BigDecimal safety = material.getSafetyStock() == null ? BigDecimal.ZERO : material.getSafetyStock();
            BigDecimal quantity = inventory.getQuantity() == null ? BigDecimal.ZERO : inventory.getQuantity();
            if (belowSafety && quantity.compareTo(safety) >= 0) {
                continue;
            }
            views.add(new InventoryView(inventory.getId(), inventory.getWarehouseId(),
                    warehouse == null ? null : warehouse.getWarehouseNo(),
                    warehouse == null ? null : warehouse.getWarehouseName(),
                    inventory.getFactoryId(), inventory.getMaterialId(), material.getMaterialCode(),
                    material.getMaterialName(), material.getUnit(), quantity, safety,
                    quantity.compareTo(safety) < 0, inventory.getUpdatedAt()));
        }
        return views;
    }

    /** 库存行总数（按当前筛选条件）。 */
    @Transactional(readOnly = true)
    public long countInventory(Long warehouseId, Long factoryId, Long materialId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        Long effectiveFactory = effectiveFactory(factoryId);
        if (effectiveFactory != null) {
            conditions.add("factory_id = ?");
            arguments.add(effectiveFactory);
        }
        if (warehouseId != null) {
            conditions.add("warehouse_id = ?");
            arguments.add(warehouseId);
        }
        if (materialId != null) {
            conditions.add("material_id = ?");
            arguments.add(materialId);
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        return inventoryMapper.selectCountByQuery(query);
    }

    // ------------------------------------------------------------------ 公共

    /** 读取仓库，不存在抛 404。 */
    public BizWarehouse requireWarehouse(long id) {
        BizWarehouse warehouse = warehouseMapper.selectOneById(id);
        if (warehouse == null) {
            throw new BizException(404, "仓库不存在：" + id);
        }
        return warehouse;
    }

    /** 读取物料，不存在抛 404。 */
    public BizMaterial requireMaterial(long id) {
        BizMaterial material = materialMapper.selectOneById(id);
        if (material == null) {
            throw new BizException(404, "物料不存在：" + id);
        }
        return material;
    }

    /**
     * 写入数据范围校验：管理员和机器令牌不受限，厂长只能操作本厂。
     *
     * @param factoryId 工厂 ID
     */
    public void requireFactoryWrite(Long factoryId) {
        if (factoryId == null) {
            throw new BizException(400, "必须指定工厂");
        }
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            Long ownFactoryId = SecurityUtils.factoryId();
            if (ownFactoryId == null || !ownFactoryId.equals(factoryId)) {
                throw new BizException(403, "只能操作本厂仓库");
            }
            return;
        }
        throw new BizException(403, "无仓储写入权限");
    }

    /** 读取范围：厂长强制本厂，其他角色按传入工厂或全部。 */
    public Long effectiveFactory(Long factoryId) {
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            return SecurityUtils.factoryId();
        }
        return factoryId;
    }

    /** 数量统一保留 2 位小数并截断。 */
    public static BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.DOWN);
        }
        return value.setScale(2, RoundingMode.DOWN);
    }

    /** 单价统一保留 4 位小数并截断。 */
    public static BigDecimal normalizePrice(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.DOWN);
        }
        return value.setScale(4, RoundingMode.DOWN);
    }

    /** 损耗率统一保留 2 位小数并截断，范围 0–100。 */
    public static BigDecimal normalizeLossRate(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.DOWN);
        }
        BigDecimal rate = value.setScale(2, RoundingMode.DOWN);
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BizException(400, "损耗率必须在 0–100 之间");
        }
        return rate;
    }

    /**
     * 行金额计算：数量 × 单价，按分截断。
     *
     * @param quantity  数量
     * @param unitPrice 单价
     * @return 金额（分）
     */
    public static long amountInCents(BigDecimal quantity, BigDecimal unitPrice) {
        BigDecimal amount = normalizeQuantity(quantity).multiply(normalizePrice(unitPrice))
                .multiply(BigDecimal.valueOf(100));
        return amount.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /** 去首尾空格，空字符串转 null。 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 同一工厂只能有一个主仓。 */
    private void assertSingleMainWarehouse(Long factoryId, Long excludeId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        conditions.add("factory_id = ?");
        arguments.add(factoryId);
        conditions.add("warehouse_type = 'MAIN'");
        conditions.add("status = 'ACTIVE'");
        if (excludeId != null) {
            conditions.add("id <> ?");
            arguments.add(excludeId);
        }
        if (warehouseMapper.selectCountByQuery(QueryWrapper.create()
                .where(String.join(" AND ", conditions), arguments.toArray())) > 0) {
            throw new BizException(409, "该工厂已存在启用中的主仓");
        }
    }

    /** 工厂需要保留至少一个启用主仓。 */
    private void assertAnotherMainWarehouse(Long factoryId, Long excludeId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        conditions.add("factory_id = ?");
        arguments.add(factoryId);
        conditions.add("warehouse_type = 'MAIN'");
        conditions.add("status = 'ACTIVE'");
        conditions.add("id <> ?");
        arguments.add(excludeId);
        if (warehouseMapper.selectCountByQuery(QueryWrapper.create()
                .where(String.join(" AND ", conditions), arguments.toArray())) == 0) {
            throw new BizException(409, "工厂必须保留一个启用中的主仓");
        }
    }

    /** 仓库库存总量。 */
    private BigDecimal totalQuantity(long warehouseId) {
        List<BizInventory> list = inventoryMapper.selectListByQuery(QueryWrapper.create()
                .where("warehouse_id = ?", warehouseId));
        BigDecimal total = BigDecimal.ZERO;
        for (BizInventory inventory : list) {
            if (inventory.getQuantity() != null) {
                total = total.add(inventory.getQuantity());
            }
        }
        return total;
    }

    /** 物料在全部仓库的库存合计。 */
    private BigDecimal materialQuantity(long materialId) {
        List<BizInventory> list = inventoryMapper.selectListByQuery(QueryWrapper.create()
                .where("material_id = ?", materialId));
        BigDecimal total = BigDecimal.ZERO;
        for (BizInventory inventory : list) {
            if (inventory.getQuantity() != null) {
                total = total.add(inventory.getQuantity());
            }
        }
        return total;
    }

    /** 批量加载仓库。 */
    private Map<Long, BizWarehouse> loadWarehouses(List<Long> ids) {
        Map<Long, BizWarehouse> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<BizWarehouse> list = warehouseMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
        for (BizWarehouse warehouse : list) {
            result.put(warehouse.getId(), warehouse);
        }
        return result;
    }

    /** 批量加载物料。 */
    private Map<Long, BizMaterial> loadMaterials(List<Long> ids) {
        Map<Long, BizMaterial> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<BizMaterial> list = materialMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
        for (BizMaterial material : list) {
            result.put(material.getId(), material);
        }
        return result;
    }

    /** 构建仓库视图，补齐库存概览。 */
    private List<WarehouseView> buildWarehouseViews(List<BizWarehouse> warehouses) {
        if (warehouses.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> materialCounts = new LinkedHashMap<>();
        Map<Long, BigDecimal> quantityTotals = new LinkedHashMap<>();
        for (BizWarehouse warehouse : warehouses) {
            List<BizInventory> list = inventoryMapper.selectListByQuery(QueryWrapper.create()
                    .where("warehouse_id = ?", warehouse.getId()));
            BigDecimal total = BigDecimal.ZERO;
            for (BizInventory inventory : list) {
                if (inventory.getQuantity() != null) {
                    total = total.add(inventory.getQuantity());
                }
            }
            materialCounts.put(warehouse.getId(), list.size());
            quantityTotals.put(warehouse.getId(), total);
        }
        List<WarehouseView> views = new ArrayList<>(warehouses.size());
        for (BizWarehouse warehouse : warehouses) {
            views.add(WarehouseView.from(warehouse,
                    warehouse.getManagerUserId() == null ? null : "用户#" + warehouse.getManagerUserId(),
                    materialCounts.getOrDefault(warehouse.getId(), 0),
                    quantityTotals.getOrDefault(warehouse.getId(), BigDecimal.ZERO)));
        }
        return views;
    }

    /** 构建物料视图，补齐库存合计。 */
    private List<MaterialView> buildMaterialViews(List<BizMaterial> materials) {
        if (materials.isEmpty()) {
            return List.of();
        }
        List<Long> materialIds = materials.stream().map(BizMaterial::getId).toList();
        List<BizInventory> inventories = inventoryMapper.selectListByQuery(QueryWrapper.create()
                .where("material_id IN (" + placeholders(materialIds.size()) + ")", materialIds.toArray()));
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        for (BizInventory inventory : inventories) {
            BigDecimal quantity = inventory.getQuantity() == null ? BigDecimal.ZERO : inventory.getQuantity();
            totals.merge(inventory.getMaterialId(), quantity, BigDecimal::add);
        }
        List<MaterialView> views = new ArrayList<>(materials.size());
        for (BizMaterial material : materials) {
            views.add(MaterialView.from(material, totals.getOrDefault(material.getId(), BigDecimal.ZERO)));
        }
        return views;
    }

    /** 生成 IN 子句占位符。 */
    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }
}
