package com.company.platform.order.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 新建物料命令。
 *
 * <p>单价最多 4 位小数、损耗率 2 位小数（百分比），由服务层按 V1.4 口径校验；
 * 数量类字段统一保留 2 位小数。</p>
 */
public record MaterialCreateCommand(
        @NotBlank(message = "物料编码不能为空") @Size(max = 64, message = "物料编码不能超过 64 字") String materialCode,
        @NotBlank(message = "物料名称不能为空") @Size(max = 128, message = "物料名称不能超过 128 字") String materialName,
        @NotBlank(message = "物料类型不能为空") String materialType,
        @Size(max = 255, message = "规格不能超过 255 字") String spec,
        @NotBlank(message = "计量单位不能为空") @Size(max = 16, message = "计量单位不能超过 16 字") String unit,
        @NotNull(message = "采购单价不能为空")
        @DecimalMin(value = "0", message = "采购单价不能为负数")
        @Digits(integer = 12, fraction = 4, message = "采购单价最多 4 位小数") BigDecimal unitPrice,
        @DecimalMin(value = "0", message = "损耗率不能为负数")
        @Digits(integer = 3, fraction = 2, message = "损耗率最多 2 位小数") BigDecimal lossRate,
        @DecimalMin(value = "0", message = "安全库存不能为负数")
        @Digits(integer = 12, fraction = 2, message = "安全库存最多 2 位小数") BigDecimal safetyStock,
        @Size(max = 500, message = "备注不能超过 500 字") String remark
) {
}
