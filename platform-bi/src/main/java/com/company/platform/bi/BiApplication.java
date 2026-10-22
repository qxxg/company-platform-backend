package com.company.platform.bi;

import com.company.platform.bi.config.BiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 首页大屏与 BI 聚合服务启动类。
 *
 * <p>本服务只读取业务库并写入自己的 {@code company_bi} 集市，不直接修改订单、
 * 财务、设备等业务表。所有对外查询都要求有效 JWT，并按当前用户角色和数据范围
 * 在聚合层再次过滤。</p>
 *
 * <p>定时 ETL 默认 5 分钟运行一次：先从业务库抽取到内存快照，再在一个本地事务中
 * 整体替换 BI 事实表。失败时保留上一次成功快照，页面仍可展示最后更新时间。</p>
 */
@SpringBootApplication(scanBasePackages = "com.company.platform")
@EnableScheduling
@EnableConfigurationProperties(BiProperties.class)
public class BiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiApplication.class, args);
    }
}
