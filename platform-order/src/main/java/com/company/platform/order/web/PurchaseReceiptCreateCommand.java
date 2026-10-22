package com.company.platform.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建采购入库单命令。
 *
 * <p>入库单先落为草稿（DRAFT），确认收货（RECEIVED）时才真正增加库存。行明细
 * 只提交物料和数量，单价、损耗率在服务端按物料档案快照，避免前端传入错误
 * 价格导致材料成本失真。</p>
 */
public record PurchaseReceiptCreateCommand(
        @NotNull(message = "收货仓库不能为空") Long warehouseId,
        @NotBlank(message = "供应商不能为空") @Size(max = 200, message = "供应商不能超过 200 字") String supplierName,
        @NotNull(message = "收货日期不能为空") LocalDate receiptDate,
        @Size(max = 500, message = "备注不能超过 500 字") String remark,
        @NotEmpty(message = "入库明细不能为空")
        @Valid List<Line> lines
) {

    /**
     * 入库单行：只提交物料和数量。
     *
     * @param materialId 物料 ID
     * @param quantity   入库数量，2 位小数，必须大于 0
     * @param remark     行备注
     */
    public record Line(
            @NotNull(message = "入库物料不能为空") Long materialId,
            @NotNull(message = "入库数量不能为空")
            @DecimalMin(value = "0.01", message = "入库数量必须大于 0")
            @Digits(integer = 12, fraction = 2, message = "入库数量最多 2 位小数") java.math.BigDecimal quantity,
            @Size(max = 500, message = "行备注不能超过 500 字") String remark
    ) {
    }
}
