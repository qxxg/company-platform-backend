package com.company.platform.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新建跨厂调拨单命令。
 *
 * <p>调出、调入仓库必须不同且都处于启用状态；明细只提交物料和数量。调拨不允许
 * 跨物料类型混用的问题由服务端逐行校验物料是否存在且启用。</p>
 */
public record StockTransferCreateCommand(
        @NotNull(message = "调出仓库不能为空") Long fromWarehouseId,
        @NotNull(message = "调入仓库不能为空") Long toWarehouseId,
        LocalDate plannedDate,
        @Size(max = 500, message = "备注不能超过 500 字") String remark,
        @NotEmpty(message = "调拨明细不能为空")
        @Valid List<Line> lines
) {

    /**
     * 调拨单行：只提交物料和数量。
     *
     * @param materialId 物料 ID
     * @param quantity   调拨数量，2 位小数，必须大于 0
     * @param remark     行备注
     */
    public record Line(
            @NotNull(message = "调拨物料不能为空") Long materialId,
            @NotNull(message = "调拨数量不能为空")
            @DecimalMin(value = "0.01", message = "调拨数量必须大于 0")
            @Digits(integer = 12, fraction = 2, message = "调拨数量最多 2 位小数") BigDecimal quantity,
            @Size(max = 500, message = "行备注不能超过 500 字") String remark
    ) {
    }
}
