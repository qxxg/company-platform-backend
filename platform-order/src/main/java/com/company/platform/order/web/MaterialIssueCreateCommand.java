package com.company.platform.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新建工单领料单命令。
 *
 * <p>工单由路径参数确定，不允许前端指定；领料仓库必须与工单同厂。明细只提交物料
 * 和数量，单价与损耗率在服务端按物料档案快照，前端不能自定义价格。</p>
 */
public record MaterialIssueCreateCommand(
        @NotNull(message = "领料仓库不能为空") Long warehouseId,
        @Size(max = 500, message = "备注不能超过 500 字") String remark,
        @NotEmpty(message = "领料明细不能为空")
        @Valid List<Line> lines
) {

    /**
     * 领料单行：只提交物料和数量。
     *
     * @param materialId 物料 ID
     * @param quantity   领料数量，2 位小数，必须大于 0
     * @param remark     行备注
     */
    public record Line(
            @NotNull(message = "领料物料不能为空") Long materialId,
            @NotNull(message = "领料数量不能为空")
            @DecimalMin(value = "0.01", message = "领料数量必须大于 0")
            @Digits(integer = 12, fraction = 2, message = "领料数量最多 2 位小数") BigDecimal quantity,
            @Size(max = 500, message = "行备注不能超过 500 字") String remark
    ) {
    }
}
