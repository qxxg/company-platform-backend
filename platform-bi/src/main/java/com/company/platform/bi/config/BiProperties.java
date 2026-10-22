package com.company.platform.bi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BI 服务配置。
 *
 * <p>业务库连接与业务服务分开配置，便于生产环境把 BI 账号收紧为只读。当前开发
 * 环境共用 {@code company_app}；生产环境应为 `company_bi` 建独立 ETL 账号，并让
 * 平台 BI 查询账号只拥有 {@code company_bi} 的 SELECT 权限。</p>
 */
@ConfigurationProperties(prefix = "app.bi")
public class BiProperties {

    /** 定时刷新间隔，默认 5 分钟，符合 V1.4 经营指标 5–15 分钟刷新要求。 */
    private long refreshMs = 300_000L;

    /** 启动后延迟刷新，避免服务刚启动、Nacos 尚未就绪时立即抢占连接。 */
    private long initialDelayMs = 15_000L;

    /** 业务主库：客户、合同、应收、收款、费用、借款、审批。 */
    private Source platform = new Source();

    /** 订单分片 0：订单、设备、排产、仓储。 */
    private Source order0 = new Source();

    /** 订单分片 1：仅订单物理表。 */
    private Source order1 = new Source();

    public long getRefreshMs() {
        return refreshMs;
    }

    public void setRefreshMs(long refreshMs) {
        this.refreshMs = refreshMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public Source getPlatform() {
        return platform;
    }

    public void setPlatform(Source platform) {
        this.platform = platform;
    }

    public Source getOrder0() {
        return order0;
    }

    public void setOrder0(Source order0) {
        this.order0 = order0;
    }

    public Source getOrder1() {
        return order1;
    }

    public void setOrder1(Source order1) {
        this.order1 = order1;
    }

    /** 单个只读业务库连接配置。 */
    public static class Source {

        private String url;
        private String username;
        private String password;
        private int poolSize = 2;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }
}
