package com.company.platform.common.web.filter;

import com.company.platform.common.core.trace.TraceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet 请求追踪过滤器。
 *
 * <p>优先复用网关透传的 X-Request-Id；如果直接访问业务服务则生成新值。
 * 值写入 MDC 和响应头，finally 中清理，避免线程池复用导致追踪号串请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceId.HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        TraceId.set(traceId);
        response.setHeader(TraceId.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceId.clear();
        }
    }
}
