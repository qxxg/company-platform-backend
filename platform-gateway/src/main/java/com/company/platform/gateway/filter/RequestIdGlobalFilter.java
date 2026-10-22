package com.company.platform.gateway.filter;

import com.company.platform.common.core.trace.TraceId;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 网关请求追踪过滤器。
 *
 * <p>为每个请求生成或复用 X-Request-Id，写回响应头并透传给下游服务。
 * 它是全局过滤器且优先级最高，保证后续日志、鉴权和路由都能拿到同一个
 * traceId。</p>
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    /** 生成 traceId 并注入下游请求头。 */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(TraceId.HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        exchange.getResponse().getHeaders().set(TraceId.HEADER, requestId);
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate().header(TraceId.HEADER, requestId).build())
                .build();
        return chain.filter(mutatedExchange);
    }

    /** 最高优先级，先于鉴权和路由执行。 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
