package com.company.platform.order.web;

import java.util.List;

/**
 * 站内消息分页结果。
 *
 * <p>同时返回未读总数，顶部消息铃铛可以直接复用该值，不需要再发一次请求。</p>
 */
public record MessagePageView(List<StationMessageView> records, long total, long pageNumber,
                              long pageSize, long unreadCount) {
}
