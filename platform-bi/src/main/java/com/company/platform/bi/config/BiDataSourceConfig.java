package com.company.platform.bi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * BI 服务多数据源配置。
 *
 * <p>主数据源是公司 BI 集市 {@code company_bi}，负责保存事实表、指标口径和 ETL 运行
 * 记录；另外三个数据源只用于抽取业务库：{@code company_platform}、
 * {@code company_order0}、{@code company_order1}。业务库连接池保持较小，
 * 避免 BI 定时任务影响在线业务。</p>
 *
 * <p>不开启 Spring Boot 的 SQL 初始化，避免每次启动重复执行建表脚本。建表与权限
 * 由 {@code sql/18-bi-dashboard.sql} 统一管理。</p>
 */
@Configuration
public class BiDataSourceConfig {

    /** 绑定主数据源连接信息；application.yml 只暴露开发环境默认值。 */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties biDataSourceProperties() {
        return new DataSourceProperties();
    }

    /** 主数据源连接池；Hikari 参数与业务服务保持一致。 */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /** BI 集市 JdbcTemplate，ETL 写入和看板查询都走这里。 */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** 业务主库只读连接。 */
    @Bean(name = "platformDataSource")
    public DataSource platformDataSource(BiProperties properties) {
        return buildSource(properties.getPlatform(), "bi-source-platform");
    }

    /** 订单分片 0 只读连接。 */
    @Bean(name = "order0DataSource")
    public DataSource order0DataSource(BiProperties properties) {
        return buildSource(properties.getOrder0(), "bi-source-order0");
    }

    /** 订单分片 1 只读连接。 */
    @Bean(name = "order1DataSource")
    public DataSource order1DataSource(BiProperties properties) {
        return buildSource(properties.getOrder1(), "bi-source-order1");
    }

    @Bean(name = "platformJdbc")
    public JdbcTemplate platformJdbc(@Qualifier("platformDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "order0Jdbc")
    public JdbcTemplate order0Jdbc(@Qualifier("order0DataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "order1Jdbc")
    public JdbcTemplate order1Jdbc(@Qualifier("order1DataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** 创建只读业务库连接池；初始化失败时快速报错，由 ETL 记录失败而不影响服务启动。 */
    private DataSource buildSource(BiProperties.Source source, String poolName) {
        if (source == null || source.getUrl() == null || source.getUrl().isBlank()) {
            throw new IllegalStateException("BI 数据源未配置：" + poolName);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(source.getUrl());
        config.setUsername(source.getUsername());
        config.setPassword(source.getPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setPoolName(poolName);
        config.setMaximumPoolSize(Math.max(1, source.getPoolSize()));
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000L);
        config.setInitializationFailTimeout(10_000L);
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }
}
