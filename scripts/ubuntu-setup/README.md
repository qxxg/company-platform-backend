# Ubuntu 192.168.3.44 中间件安装说明

> 安装日期：2026-10-20
> 主机：Ubuntu 26.04.1 LTS (resolute)，4 核 / 15 GiB 内存，系统盘约 43 GiB 可用
> 登录：`wy` / `root`（sudo 同密码）
> 用途：公司管理系统开发/联调环境（MySQL 双实例模拟分库分表、Redis、EMQX、IoTDB）

## 1. 组件与端口

| 组件 | 版本 | 端口 | 账号/密码 | 说明 |
|---|---|---|---|---|
| MySQL 实例 1 | 9.7.2 LTS | 3306 | root / root；远程 wy / root | `demo_shard0` + `demo_meta` |
| MySQL 实例 2 | 9.7.2 LTS | 3307 | root / root；远程 wy / root | `demo_shard1` |
| Redis | 8.10.2 | 6379 | 密码 root | 源码编译，AOF 开启 |
| EMQX 开源版 | 5.8.9 | 1883 / 8083 / 8084 / 8883 / 18083 | Dashboard admin / **root@123** | MQTT Broker；Dashboard 密码最少 8 位，故不能使用 root |
| Apache IoTDB | 2.0.11 | 6667；REST 18080 | root / root | 单机版时序数据库 |

所有服务均已设置开机自启。

## 2. 验收结果（2026-10-20 10:22 UTC）

| 检查项 | 结果 |
|---|---|
| mysql / mysql2 / redis-server / emqx / iotdb | 全部 `active`、`enabled` |
| MySQL 3306 | 9.7.2，`demo_meta`、`demo_shard0` 可查，`orders` 3 行 |
| MySQL 3307 | 9.7.2，`demo_shard1` 可查，`orders` 3 行 |
| Redis | `PONG`，版本 8.10.2，端口 6379 |
| EMQX | 5.8.9 已启动，Dashboard HTTP 200，`admin / root@123` 登录成功 |
| IoTDB | 6667/18080 监听，`root.demo` 库与示例测点可查 |
| 远程 MySQL 3306 | `mysql -h192.168.3.44 -P3306 -uwy -proot` 验证通过 |
| 远程 MySQL 3307 | `mysql -h192.168.3.44 -P3307 -uwy -proot` 验证通过 |
| 远程 Redis | `redis-cli -h192.168.3.44 -p6379 -a root ping` 返回 `PONG` |
| 远程 IoTDB | `start-cli.sh -h192.168.3.44 -p6667` 可查 `root.demo` |
| 远程 EMQX Dashboard | `http://192.168.3.44:18083/` 返回 HTTP 200 |

一键复验：

```bash
bash /home/wy/setup/99-verify.sh
```

## 3. 分库分表示例

`demo_meta.shard_route` 记录分片路由：

| shard_no | 实例 | 数据库 | id 范围 |
|---|---|---|---|
| 0 | 3306 | demo_shard0 | 0 – 4,999,999 |
| 1 | 3307 | demo_shard1 | 5,000,000 – 9,999,999 |

每个分片有相同的 `orders` 表结构，示例数据已插入。

## 4. 客户端连接

```text
MySQL 3306 : jdbc:mysql://192.168.3.44:3306/<db>    wy / root
MySQL 3307 : jdbc:mysql://192.168.3.44:3307/<db>    wy / root
Redis      : 192.168.3.44:6379                      密码 root
MQTT       : tcp://192.168.3.44:1883                默认未开启认证（仅内网开发）
EMQX Dash  : http://192.168.3.44:18083              admin / root@123
IoTDB      : 192.168.3.44:6667                      root / root
IoTDB REST : http://192.168.3.44:18080/rest/v1/...
```

## 5. 常用命令

```bash
# 服务状态
systemctl status mysql mysql2 redis-server emqx iotdb --no-pager

# MySQL
mysql -h127.0.0.1 -P3306 -uroot -proot
mysql -h127.0.0.1 -P3307 -uroot -proot

# Redis
redis-cli --no-auth-warning -a root ping

# EMQX
sudo emqx ctl status
sudo emqx ctl admins passwd admin 'root@123'
# Dashboard: http://192.168.3.44:18083

# IoTDB CLI
/opt/iotdb/sbin/start-cli.sh -h 127.0.0.1 -p 6667 -u root -pw root

# 日志
sudo journalctl -u mysql -n 100 --no-pager
sudo journalctl -u mysql2 -n 100 --no-pager
sudo journalctl -u redis-server -n 100 --no-pager
sudo journalctl -u emqx -n 100 --no-pager
sudo journalctl -u iotdb -n 100 --no-pager
```

## 6. 数据目录与配置

| 组件 | 数据目录 | 配置 |
|---|---|---|
| MySQL 3306 | `/var/lib/mysql` | `/etc/mysql/mysql.conf.d/` |
| MySQL 3307 | `/var/lib/mysql2` | `/etc/mysql/mysql2.cnf` |
| Redis | `/var/lib/redis` | `/etc/redis/redis.conf` |
| EMQX | `/var/lib/emqx` | `/etc/emqx/` |
| IoTDB | `/opt/iotdb/data` | `/opt/iotdb/conf/iotdb-system.properties` |

IoTDB 配置要点：

- `dn_rpc_address=0.0.0.0`，`dn_rpc_port=6667`
- 内部 RPC/共识端口仅监听 127.0.0.1（10710–10760）
- REST 服务 `enable_rest_service=true`，端口 18080
- systemd 服务 `iotdb.service`，`Type=forking`，`ExecStart=/opt/iotdb/sbin/start-standalone.sh`

## 7. 安装脚本

脚本位于本仓库 `scripts/ubuntu-setup/`，按顺序执行：

| 脚本 | 作用 |
|---|---|
| `fix-apt.sh` | 切换中科大镜像并修复 apt |
| `01-prereqs.sh` | OpenJDK 17、编译工具、unzip 等依赖 |
| `02-mysql.sh` | MySQL 9.7 主实例 3306 + 示例分片 0 |
| `02b-mysql-finish.sh` | 第二实例 3307（数据目录 `/var/lib/mysql2`）+ 示例分片 1 |
| `03-redis.sh` | Redis 8.10.2 源码编译与 systemd |
| `04-emqx.sh` | EMQX 5.8.9 安装、启动、Dashboard 密码 |
| `05-iotdb.sh` | IoTDB 2.0.11 安装、配置、systemd、CLI 验证 |
| `99-verify.sh` | 全组件统一验收 |

> 注：IoTDB 安装包约 114 MB，脚本优先使用 `/home/wy/setup/apache-iotdb-2.0.11-all-bin.zip`，不存在时再联网下载。

## 8. 安全提示

当前为内网开发环境，使用了简单密码。如需对更大范围开放，请务必：

1. 修改 MySQL / Redis / EMQX / IoTDB 默认密码；
2. 为 EMQX 开启认证与 ACL，不要保持匿名接入；
3. 使用防火墙仅放行 192.168.3.0/24；
4. 生产环境启用 TLS（MySQL、Redis、EMQX、IoTDB）。
