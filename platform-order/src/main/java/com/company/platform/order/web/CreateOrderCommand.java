package com.company.platform.order.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建订单请求。
 *
 * <p>金额字段名为 totalAmount，但语义是“分”，不是元。前端必须完成元到分的
 * 截断转换后再提交，服务端不会再做四舍五入。</p>
 *
 * <p>客户、工厂和合同关联的完整校验将在客户/财务模块接入后补充。</p>
 */
public record CreateOrderCommand(
        /** 客户编号，必填；客户模块落地后改为客户主键。 */
        @NotNull(message = "customerId is required") Long customerId,
        /** 财务已录入合同 ID，必填；服务端会校验状态、归属和额度。 */
        @NotNull(message = "contractId is required") Long contractId,
        /** 生产工厂编号，必填；订单不允许跨工厂。 */
        @NotNull(message = "factoryId is required") Long factoryId,
        /** 订单总额，单位分，至少 1 分。 */
        @NotNull(message = "totalAmount is required")
        @Min(value = 1, message = "totalAmount must be at least 1 cent") Long totalAmount,
        /** 币种，最大 8 字符；为空时服务端按 CNY 处理。 */
        @Size(max = 8, message = "currency is too long") String currency,
        /** 备注，最大 500 字符。 */
        @Size(max = 500, message = "remark is too long") String remark
) {
}
