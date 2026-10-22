package com.company.platform.order.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.single.config.SingleRuleConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * ShardingSphere-JDBC 分库分表配置。
 *
 * <pre>
 *   ds_0 = MySQL 192.168.3.44:3306 / company_order0
 *   ds_1 = MySQL 192.168.3.44:3307 / company_order1
 *   t_order -> ds_${0..1}.t_order_${0..3}
 *   分库键：order_no % 2
 *   分表键：(order_no >> 1) % 4，等价于 (order_no / 2) % 4
 * </pre>
 *
 * <p>按 order_no 查询会精确路由到唯一物理表；按 customer_id、factory_id、
 * 状态查询会广播到 8 张物理表后合并。客户维度查询成为热点后，应增加客户与
 * 订单号的映射索引表，而不是随意增加第二个分片键。</p>
 *
 * <p>分片算法使用 INLINE 而不是 MOD：ShardingSphere 5.5 的 MOD 算法属于
 * 自动分表模式，不能与显式列出的 t_order_0..3 节点同时使用。</p>
 */
@Configuration
@EnableConfigurationProperties(ShardingProperties.class)
public class ShardingConfig {

    /**
     * 创建 ShardingSphere 数据源。
     *
     * <p>不使用 Spring Boot 的单数据源自动配置，因为这里需要把两个独立
     * Hikari 连接池交给 ShardingSphere 管理。返回的是逻辑 DataSource，
     * MyBatis-Flex 在无感知的情况下操作逻辑表 t_order。</p>
     */
    @Bean
    public DataSource dataSource(ShardingProperties properties) throws SQLException {
        HikariDataSource ds0 = createDataSource("order-ds0", properties.getDs0());
        HikariDataSource ds1 = createDataSource("order-ds1", properties.getDs1());

        ShardingTableRuleConfiguration orderTable = new ShardingTableRuleConfiguration(
                "t_order", "ds_${0..1}.t_order_${0..3}");
        orderTable.setDatabaseShardingStrategy(
                new StandardShardingStrategyConfiguration("order_no", "database_inline"));
        orderTable.setTableShardingStrategy(
                new StandardShardingStrategyConfiguration("order_no", "table_inline"));

        ShardingRuleConfiguration shardingRule = new ShardingRuleConfiguration();
        shardingRule.setTables(List.of(orderTable));
        // 分库：order_no 的奇偶决定 ds_0 / ds_1。
        // 分表：先右移一位再对 4 取模，让同一个库内的 4 张表都均匀使用。
        //
        // 不能写成 order_no % 4：这样表序号的奇偶会与库序号完全一致，
        // ds_0 只会使用 t_order_0/2，ds_1 只会使用 t_order_1/3，一半物理表闲置。
        // SnowflakeIdGenerator 的序列号跨毫秒继续递增，保证低两位不会长期为 0。
        shardingRule.setShardingAlgorithms(Map.of(
                "database_inline", new AlgorithmConfiguration("INLINE",
                        inlineProperties("ds_${order_no % 2}")),
                "table_inline", new AlgorithmConfiguration("INLINE",
                        inlineProperties("t_order_${(order_no >> 1) % 4}"))));

        Collection<RuleConfiguration> rules = List.of(shardingRule, singleRule());
        Map<String, DataSource> dataSources = Map.of("ds_0", ds0, "ds_1", ds1);
        Properties props = new Properties();
        props.setProperty("sql-show", Boolean.toString(properties.isSqlShow()));
        return ShardingSphereDataSourceFactory.createDataSource(dataSources, rules, props);
    }

    /**
     * 设备台账、工单、队列、插队申请、站内消息、维护工单、点检记录和仓储相关表
     * 都是单表，固定放在 ds_0。
     *
     * <p>如果不声明单表规则，ShardingSphere 在双数据源下无法判断这些表位于
     * 哪个库，会报“表不存在”或把 SQL 广播到两个库。这里显式列出全部逻辑表与
     * 真实节点，订单分片表仍然只由 {@link ShardingRuleConfiguration} 管理。</p>
     *
     * <p>排产相关表与分片订单表在同一 MySQL 实例（3306），但属于不同逻辑库；
     * 工单服务通过订单号读取订单时由 ShardingSphere 路由，排产表则固定 ds_0。
     * 跨 ds_0/ds_1 的订单状态回写使用本地事务尽力保证，生产环境应改为可靠事件
     * 或最终一致方案。</p>
     */
    private static SingleRuleConfiguration singleRule() {
        SingleRuleConfiguration singleRule = new SingleRuleConfiguration();
        singleRule.setTables(List.of(
                "ds_0.t_device",
                "ds_0.t_device_status_log",
                "ds_0.t_work_order",
                "ds_0.t_device_queue",
                "ds_0.t_queue_jump_request",
                "ds_0.t_station_message",
                "ds_0.t_maintenance_order",
                "ds_0.t_inspection_record",
                "ds_0.t_warehouse",
                "ds_0.t_material",
                "ds_0.t_inventory",
                "ds_0.t_purchase_receipt",
                "ds_0.t_purchase_receipt_line",
                "ds_0.t_stock_transfer",
                "ds_0.t_stock_transfer_line",
                "ds_0.t_material_issue",
                "ds_0.t_material_issue_line",
                "ds_0.t_device_credential",
                "ds_0.t_device_order_report",
                "ds_0.t_device_image",
                "ds_0.t_device_report_staging",
                "ds_0.t_device_access_log"));
        return singleRule;
    }

    /**
     * 创建单个物理库连接池。
     *
     * <p>连接池参数刻意保持较小，适合开发机；生产环境应根据数据库最大连接数
     * 和订单服务实例数统一规划。生产密码应通过环境变量注入，不能写进仓库。</p>
     */
    private static HikariDataSource createDataSource(String poolName,
                                                     ShardingProperties.DataSourceProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName(poolName);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(5000);
        return dataSource;
    }

    /** 构造 INLINE 算法所需的 algorithm-expression 属性。 */
    private static Properties inlineProperties(String expression) {
        Properties properties = new Properties();
        properties.setProperty("algorithm-expression", expression);
        return properties;
    }
}
