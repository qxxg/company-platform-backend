package com.company.platform.order.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改仓库命令。
 *
 * <p>仓库编号和所属工厂不允许修改：跨厂移仓要走调拨或审批，避免历史库存归属
 * 变化导致账目不清。</p>
 */
public record WarehouseUpdateCommand(
        @NotBlank(message = "仓库名称不能为空") @Size(max = 128, message = "仓库名称不能超过 128 字") String warehouseName,
        @NotBlank(message = "仓库类型不能为空") String warehouseType,
        @Size(max = 255, message = "仓库地址不能超过 255 字") String address,
        Long managerUserId,
        @Size(max = 500, message = "备注不能超过 500 字") String remark
) {
}
