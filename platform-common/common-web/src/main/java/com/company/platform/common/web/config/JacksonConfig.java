package com.company.platform.common.web.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 Jackson 序列化配置。
 *
 * <p>雪花 ID 约为 19 位十进制数，超过 JavaScript
 * {@code Number.MAX_SAFE_INTEGER}（2^53-1）。如果按 JSON number 返回，
 * 浏览器解析时会丢精度，订单号、用户 ID 等关键字段会悄悄变化，因此所有
 * Long/long 统一序列化为字符串。</p>
 *
 * <p>反序列化无需特殊处理：Jackson 可以把 JSON 字符串自动转回 Long。
 * 前端也必须始终按字符串处理这些 ID。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
