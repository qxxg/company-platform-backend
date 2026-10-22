package com.company.platform.common.core.api;

import java.util.List;

/**
 * 通用分页结果。
 *
 * <p>订单查询跨 8 张分片表时，total 是 ShardingSphere 合并后的总数，
 * 不代表任何单张物理表的行数。前端分页组件应始终使用该 total。</p>
 */
public record PageResult<T>(List<T> records, long total, long pageNumber, long pageSize) {

    /** 构造空页，页码和每页条数保持调用方请求值。 */
    public static <T> PageResult<T> empty(long pageNumber, long pageSize) {
        return new PageResult<>(List.of(), 0, pageNumber, pageSize);
    }
}
