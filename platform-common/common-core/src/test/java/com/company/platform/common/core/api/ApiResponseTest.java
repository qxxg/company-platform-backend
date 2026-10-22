package com.company.platform.common.core.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 统一响应与分页结果测试。
 */
class ApiResponseTest {

    @AfterEach
    void clearTraceId() {
        com.company.platform.common.core.trace.TraceId.clear();
    }

    @Test
    void successResponseUsesCodeZero() {
        ApiResponse<String> response = ApiResponse.ok("hello");
        assertEquals(0, response.code());
        assertEquals("hello", response.data());
    }

    @Test
    void traceIdIsCopiedFromMdc() {
        com.company.platform.common.core.trace.TraceId.set("trace-123");
        ApiResponse<Void> response = ApiResponse.ok();
        assertEquals("trace-123", response.traceId());
    }

    @Test
    void emptyPageKeepsRequestedPagination() {
        PageResult<String> page = PageResult.empty(2, 20);
        assertEquals(0L, page.total());
        assertEquals(2L, page.pageNumber());
        assertEquals(20L, page.pageSize());
        assertNull(page.records().stream().findFirst().orElse(null));
    }
}
