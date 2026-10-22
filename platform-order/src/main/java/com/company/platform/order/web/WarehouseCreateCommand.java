package com.company.platform.order.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新建仓库命令。
 *
 * <p>仓库编号全公司唯一；仓库类型 MAIN 表示主仓，每个工厂只能有一个主仓。
 * 工厂必须是有权限的工厂，管理员可跨厂创建，厂长只能创建本厂仓库。</p>
 */
public record WarehouseCreateCommand(
        @NotBlank(message = "仓库编号不能为空") @Size(max = 64, message = "仓库编号不能超过 64 字") String warehouseNo,
        @NotBlank(message = "仓库名称不能为空") @Size(max = 128, message = "仓库名称不能超过 128 字") String warehouseName,
        @NotNull(message = "所属工厂不能为空") Long factoryId,
        @NotBlank(message = "仓库类型不能为空") String warehouseType,
        @Size(max = 255, message = "仓库地址不能超过 255 字") String address,
        Long managerUserId,
        @Size(max = 500, message = "备注不能超过 500 字") String remark
) {
}
