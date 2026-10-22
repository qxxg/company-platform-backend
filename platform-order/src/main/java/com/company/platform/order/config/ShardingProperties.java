package com.company.platform.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分库分表连接参数。
 *
 * <p>绑定 {@code app.sharding.*}，由 ShardingConfig 读取后创建两个独立的
 * HikariDataSource。把连接信息放在配置类而不是直接写在 Java 里，便于通过
 * 环境变量覆盖生产密码和地址。</p>
 */
@ConfigurationProperties(prefix = "app.sharding")
public class ShardingProperties {

    /** 0 号分片库：company_order0，端口 3306。 */
    private DataSourceProperties ds0 = new DataSourceProperties();
    /** 1 号分片库：company_order1，端口 3307。 */
    private DataSourceProperties ds1 = new DataSourceProperties();
    /** 是否打印 ShardingSphere 改写后的真实 SQL，开发环境建议开启。 */
    private boolean sqlShow = true;

    public DataSourceProperties getDs0() {
        return ds0;
    }

    public void setDs0(DataSourceProperties ds0) {
        this.ds0 = ds0;
    }

    public DataSourceProperties getDs1() {
        return ds1;
    }

    public void setDs1(DataSourceProperties ds1) {
        this.ds1 = ds1;
    }

    public boolean isSqlShow() {
        return sqlShow;
    }

    public void setSqlShow(boolean sqlShow) {
        this.sqlShow = sqlShow;
    }

    /** 单个分片库的连接参数（URL、用户名、密码、驱动）。 */
    public static class DataSourceProperties {
        /** JDBC URL，必须指向具体的 company_order0 或 company_order1 库。 */
        private String url;
        /** 数据库账号，生产环境应使用最小权限账号。 */
        private String username;
        /** 数据库密码，生产环境通过环境变量或密钥管理注入。 */
        private String password;

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
    }
}
