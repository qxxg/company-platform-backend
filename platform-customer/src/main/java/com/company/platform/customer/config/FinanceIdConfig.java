package com.company.platform.customer.config;

import com.company.platform.common.core.id.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 财务单据号生成配置。
 *
 * <p>报销单、借款单、还款单共用一个雪花生成器，避免同一 JVM 内多个生成器
 * 使用相同 workerId 时在并发下产生重复号。机器号必须与服务实例一一对应。</p>
 */
@Configuration
public class FinanceIdConfig {

    /**
     * 财务单据雪花 ID 生成器。
     *
     * @param workerId     机器号 0–31
     * @param datacenterId 数据中心号 0–31
     */
    @Bean
    public SnowflakeIdGenerator financeIdGenerator(
            @Value("${app.snowflake.worker-id:3}") long workerId,
            @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}
