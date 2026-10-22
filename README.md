# 公司管理系统（company-platform）

![JDK](https://img.shields.io/badge/JDK-27-orange?style=flat-square) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen?style=flat-square) ![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.3-0d6a8c?style=flat-square) ![Spring Authorization Server](https://img.shields.io/badge/Authorization%20Server-1.5.8-9b59b6?style=flat-square) ![MyBatis-Flex](https://img.shields.io/badge/MyBatis--Flex-1.11.8-2f6c9e?style=flat-square) ![ShardingSphere](https://img.shields.io/badge/ShardingSphere--JDBC-5.5.3-6a4c93?style=flat-square) ![Nacos](https://img.shields.io/badge/Nacos-3.0.3-4d7bff?style=flat-square) ![MySQL](https://img.shields.io/badge/MySQL-9.7-4479a1?style=flat-square) ![Maven](https://img.shields.io/badge/Maven-3.10-c71a36?style=flat-square) ![Vue](https://img.shields.io/badge/PC%20Web-Vue%203-42b883?style=flat-square)

以 `docs/公司管理系统-模块功能规划-v1.4.md` 的业务规则为基线，采用 Spring Cloud 微服务 + Vue 3 PC Web 的可运行第一阶段骨架。当前版本重点完成**统一认证、网关、基础系统数据、客户与合同主数据、订单写入与分库分表、财务应收/收款/开票/对账、费用报销与借款/还款、设备台账与厂内排产队列、设备维护与点检、仓储（仓库/物料/采购入库/跨厂调拨）、流程审批中心、设备数据上报（MQTT/CoAP/HTTP 与 IoTDB）、本地 AI 助手（只读）、首页大屏与 BI 聚合、PC Web 基础业务页面**。

> 代码已拆分为两个独立仓库：本仓库（后端，`company-platform-backend`）与前端仓库 `company-platform-frontend`（目录 `company-platform-web`）。前端只通过网关 `/api/**` 访问后端，不依赖后端源码。

> V1.4 是当前有效版本，V1.0–V1.3 已废弃。业务实现时必须继续遵守 V1.4 中的金额截断、合同关联、信用与逾期、排队审批、MQTT/CoAP 上报等 33 条已确认规则。

## 0. 项目概览

> 交付重点概览：本章集中说明**项目结构**、**功能**、**技术架构**与**部署启动流程**，各对应章节 1–11 再做细节展开。

### 0.1 项目结构

后端仓库 `company-platform-backend`（本地目录 `company-platform`）为单 Maven 多模块 + Spring Cloud 微服务后端；PC Web 前端已拆分为独立仓库 `company-platform-frontend`（本地目录 `company-platform-web`），通过网关 `/api/**` 访问后端，前端不依赖后端源码。

```text
company-platform/                       # 后端 company-platform-backend
├── pom.xml                             # 聚合父工程：JDK 27 / Spring Boot 3.5 / Spring Cloud 2025.0.3 / Spring Cloud Alibaba 2025.0.0.0
├── build.log                           # 本地构建输出
├── README.md                           # 本文档
├── config/                             # 本地配置（如 application-iot.yml）
├── data/                               # 本地运行时数据（git 忽略）
├── docs/                               # 业务规则与需求文档（V1.4 为有效基线）
├── sql/                                # 数据库初始化 / 修复脚本（01~18）
├── scripts/                            # 脚本
│   ├── dev/                            #   启动 / 停止 / 状态 / 构建 / 冒烟
│   ├── remote/                         #   远程库初始化 / 清理冒烟数据 / 验证
│   └── ubuntu-setup/                  #   中间件安装
├── platform-ai/                        # 本地 AI 助手（Ollama + AgentScope ReAct，只读）
├── platform-approval/                  # 流程审批中心（审批定义 / 实例 / 待办 / 业务动作）
├── platform-auth/                      # 统一认证（OAuth2 + JWT，授权码 + PKCE）
├── platform-bi/                        # 首页大屏 / BI（只读 ETL + 指标语义层）
├── platform-common/                    # 公共模块（common-web / common-bom）
├── platform-customer/                  # 客户 / 合同 / 财务 / 费用报销 / 借款 / 信用
├── platform-gateway/                   # API 网关
├── platform-order/                     # 订单 / 设备 / 生产 / 仓储 / 消息
└── platform-system/                    # 系统 / 用户 / 权限 / 工厂
```

### 0.2 功能

本阶段（V1.4）已交付能力：统一认证、网关、基础系统数据；客户与合同主数据、信用与逾期；订单写入与分库分表；财务应收/收款/开票/对账；费用报销与借款/还款；设备台账与厂内排产队列、维护与点检；仓储（仓库/物料/采购入库/跨厂调拨）；流程审批中心；设备数据上报（MQTT/CoAP/HTTP + IoTDB）；本地 AI 助手（只读）；首页大屏与 BI 聚合；PC Web 基础业务页面。V1.0–V1.3 已废弃；V1.4 的金额截断、合同关联、信用/逾期、排队审批、MQTT/CoAP 上报等 33 条已确认规则继续生效。

### 0.3 技术架构

JDK 27 + Spring Boot 3.5 + Spring Cloud 2025.0.3 + Spring Cloud Alibaba 2025.0.0.0（Nacos / Gateway）+ Spring Security 6 / Spring Authorization Server 1.5；MyBatis-Flex、ShardingSphere-JDBC、AgentScope、Ollama；PC Web 为 Vue 3 + Vite 7 + Element Plus + Pinia + Vue Router + Axios + ECharts 6。

### 0.4 部署启动流程

1. 构建：`powershell -ExecutionPolicy Bypass -File scripts/dev/build.ps1`；
2. 数据库初始化：`sql/01~18` 顺序执行（见第 4.3 节）；
3. Nacos 注册中心：见第 4.4 节；
4. 中间件依赖：见第 2 节；
5. 服务启动：见第 4.5 节；
6. PC Web 前端启动：见第 4.6 节；
7. 本地 Ollama 模型：见第 4.7 节；
8. 冒烟测试：见第 4.8 节。

各步骤的详细命令、端口与排障说明见对应章节。

## 1. 当前完成情况

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| Nacos 注册中心 | 已完成 | 外部部署于 `你的IP地址:8848`，控制台 `你的IP地址:8080`；原 `platform-registry` 模块已移除 |
| `platform-auth` | 已完成基础能力 | Spring Authorization Server、JWT/JWK、OAuth2 授权码 + PKCE、客户端凭证、JDBC 客户端与授权持久化、种子用户 |
| `platform-system` | 已完成基础能力 | JWT 资源服务器、`/me`、用户列表、工厂列表、MyBatis-Flex |
| `platform-customer` | 已完成基础能力 | 客户主数据、归属数据范围、合同录入、信用应收上下文、财务应收与收款核销、手工开票、客户对账单、费用报销、借款与还款、订单前置校验内部接口、信用额度审批落地内部接口 |
| `platform-approval` | 已完成基础能力 | 流程审批中心：审批定义（金额档位）、审批实例、待办任务、审批轨迹、待办与我的申请、业务动作执行器（信用额度调整、队列插队、费用报销、借款） |
| `platform-ai` | 已完成第一阶段 | 本地 Ollama + AgentScope ReAct 助手：订单、应收、报销、借款、审批待办只读查询；透传用户 JWT，不直接访问业务库；对话审计写入 `ai_chat_log` |
| `platform-bi` | 已完成第一阶段 | 首页工作台/经营大屏聚合服务：只读抽取业务库写入 `company_bi` 星型集市，5 分钟全量刷新；指标语义层 `bi_metric` 区分可计算与“待数据源”；按角色过滤数据范围；PC Web 工作台 KPI/趋势/风险区与 16:9 经营大屏 |
| `platform-order` | 已完成基础能力 | 订单创建/按订单号查询/按客户查询/分页查询、雪花订单号、ShardingSphere-JDBC 分库分表、合同与信用阻断、数据范围；设备台账、生产工单、FIFO 设备队列、插队审批、站内消息、维护工单与点检保养、仓库物料与采购入库、跨厂调拨、设备数据上报（MQTT/CoAP + IoTDB + OCR 订单号精确匹配） |
| `platform-gateway` | 已完成基础能力 | 路由、JWT 校验、CORS、请求 ID、统一 401/403 JSON |
| `platform-web` | 已完成基础版（独立仓库 `company-platform-frontend`） | Vue 3 PC Web、OAuth2 PKCE 登录、路由守卫、工作台与经营大屏、客户列表/新建/360、合同管理、信用看板、应收账款、收款核销、手工开票、客户对账、费用报销、借款与还款、订单列表/新建/详情、设备台账、排产队列、插队审批、消息中心、AI 助手、用户、工厂、权限矩阵、设备数据上报、个人中心、规划页 |
| 客户系统 | 已完成基础版 | 客户档案、来源/归属、状态、信用额度、应收上下文；图案库、报价、交接、合并待实现 |
| 财务系统 | 已完成基础版 | 合同管理、信用/逾期阻断、应收列表、收款登记与核销、手工开票登记、客户对账单、费用报销、借款/还款已实现；应付与付款、发票作废/红冲待实现 |
| 设备管理 | 部分基础 | 设备台账、能力与位置维护、设备状态（空闲/生产中/保养/故障/停用）、状态日志、维护工单与点检保养记录已实现；点检计划排程、维护工单审批待实现 |
| 订单排产/审批 | 已完成基础版 | 订单按材料/工艺/长度拆分工单、同工厂设备匹配、FIFO 队列、开始/完成工单、订单状态联动、插队申请（统一审批中心）、排队顺延站内通知已实现 |
| 流程审批 | 已完成基础版 | 新增独立 `platform-approval` 服务：审批定义（金额档位）、审批实例、待办任务、审批轨迹、待办中心与我的申请页面；已接入客户信用额度调整、设备队列插队、费用报销、借款四类业务 |
| AI 助手 | 第一阶段完成 | `platform-ai` + 本地 Ollama Qwen3-4B + AgentScope ReAct；只读查询订单、应收、报销、借款、审批待办，对话审计落库；不自动审批/付款/改信用/排产，不做生产库 NL2SQL |
| 仓储系统 | 已完成基础版 | 每厂主仓、物料档案、库存台账、采购入库（材料成本唯一来源）、跨厂调拨、工单领料与工单材料成本已实现；工单成本结转到订单、仓储审批待实现 |
| 首页大屏 | 第一阶段完成 | 新增独立 `platform-bi` 服务：只读 ETL 写入 `company_bi` 集市 + `bi_metric` 指标语义层；工作台 KPI/趋势/风险区、16:9 经营大屏、指标口径弹窗已上线；NL2SQL 未实现 |
| 设备数据上报 | 已完成基础版 | MQTT 3.1.1 订阅（QoS 0/1、保活、重连）、CoAP 服务、HTTP 联调入口；一机一密 HMAC 验签、时间戳与序号去重；订单作业数据按订单覆盖（MySQL）、自检/异常/心跳写 IoTDB、订单图片一条一单、OCR 订单号精确匹配与未匹配暂存；配置模板 `config/application-iot.yml` |

## 2. 架构与端口

```text
                    PC Web（Vue 3）
                     :5173 开发 / Nginx
                            |
                            v
                  platform-gateway :8080
                            |
           +----------------+----------------+----------------+----------------+
           |                |                |                |                |
           v                v                v                v                v
  platform-auth      platform-system   platform-customer  platform-order  platform-approval
  :9000 OAuth2/JWT   :9101             :9103 客户/合同    :9102 生产/仓储   :9104 流程审批
           |                                 |             /排产            |
           | JWK / token validation          |                |                |
           |                                 +--> MySQL 3306  |                +--> MySQL 3306
           |                                  (company_platform) +--> MySQL 3306   (company_platform)
           |                                                    (order0)
           |                                                    +--> MySQL 3307
           |                                                     (order1)

  platform-ai :9106  ──JWT 透传──►  platform-order / platform-customer / platform-approval
           |
           +──► Ollama 0.40.8  你的IP地址:11434（本地 Qwen3-4B，只读推理）

   platform-bi :9105  ──只读 ETL──►  company_platform(3306) / company_order0(3306) / company_order1(3307)
           |
           +──► 写入 company_bi(3306) 集市（事实表 + bi_metric 指标语义层 + ETL 运行记录）
           +──► 对外只查询 company_bi，不直接读写业务表

  注册中心：platform-auth / platform-system / platform-customer / platform-order / platform-approval / platform-ai / platform-bi / platform-gateway
            └──────────────► Nacos 3.0.3  你的IP地址:8848
```

服务启动后向 Nacos 注册，网关通过 `lb://platform-system`、`lb://platform-customer`、`lb://platform-order`、`lb://platform-approval`、`lb://platform-ai`、`lb://platform-bi` 从 Nacos 获取实例并负载均衡。订单服务通过 `lb://platform-customer` 调用客户/合同校验接口，审批中心通过 `lb://platform-customer` 落地审批通过后的业务变更，AI 服务通过 `lb://platform-order`、`lb://platform-customer`、`lb://platform-approval` 调用已有只读接口；三者都透传当前用户 JWT 以保留数据范围。BI 服务不复用业务接口，而是自己只读抽取三个业务库写入独立集市，再对外提供聚合查询。Nacos 与业务服务分离部署，业务进程不再内置注册中心。

中间件部署在 `你的IP地址`：MySQL 9.7.2（3306/3307）、Redis 8.10.2（6379）、EMQX 5.8.9（1883/8083/8084/8883/18083）、Apache IoTDB 2.0.11（6667，REST 18080）、Nacos 3.0.3（8848 服务端、9848/9849 gRPC、8080 控制台）。当前代码已接入 MySQL、Nacos、EMQX、IoTDB（设备状态/心跳时序）以及 Ollama；Redis 预留给后续缓存/会话场景，尚未接入业务代码。

## 3. 技术栈

- JDK 27
- Spring Boot 3.5.16
- Spring Cloud 2025.0.3 + Spring Cloud Alibaba 2025.0.0.0（Nacos Discovery、Gateway）
- Spring Security 6.5 / Spring Authorization Server 1.5.8
- MyBatis-Flex 1.11.8
- ShardingSphere-JDBC 5.5.3
- AgentScope Java 2.0.4（ReAct Agent、工具调用、会话状态）
- Ollama 0.40.8 + Qwen3-4B-Instruct-2507（本地 CPU 推理，AgentScope 走 Ollama 原生 `/api/chat` 工具调用）
- Vue 3.5 + Vite 7 + Element Plus 2.14 + Pinia + Vue Router + Axios
- MySQL 9.7.2
- Nacos Server 3.0.3（服务发现，替代 Eureka）

## 4. 构建与启动

### 4.1 前置条件

- JDK 27（本仓库开发环境使用 `zulu27-extract/zulu27.28.101-ca-jdk27.0.0-win_x64`）
- Maven 3.10（开发环境使用 `apache-maven-3.10.0`）
- Node.js 24+（开发环境使用 `D:\nodejs`，必须调用 `npm.cmd`）
- 可访问 `你的IP地址` 的 MySQL 3306/3307
- 可访问 `你的IP地址:8848` 的 Nacos 3.0.3
- 可访问 `你的IP地址:11434` 的 Ollama（仅 AI 助手需要；不启动 AI 服务时可不部署）
- 已执行数据库初始化脚本

### 4.2 构建

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/build.ps1
```

构建产物位于各模块 `target/*.jar`。

后端单元测试（不依赖 MySQL/Nacos）：

```powershell
mvn.cmd test
```

当前测试覆盖金额截断、雪花 ID 唯一性、8 个物理分片覆盖和统一响应/分页结构。

前端构建（前端是独立仓库 `company-platform-web`，与后端同级放置）：

```powershell
cd ../company-platform-web
npm.cmd install
npm.cmd run build
```

产物位于 `company-platform-web/dist/`。前端依赖与 OAuth2 回调注册说明见 `company-platform-web/README.md`。

### 4.3 数据库初始化

SQL 脚本：

| 脚本 | 目标 |
| --- | --- |
| `sql/01-platform-schema.sql` | `company_platform`：用户、角色、权限、工厂等 6 张基础表 |
| `sql/02-oauth2-schema.sql` | `company_platform`：OAuth2 客户端、授权、同意 3 张表 |
| `sql/07-customer-contract-schema.sql` | `company_platform`：客户、合同、应收辅助表及演示客户/合同 |
| `sql/08-finance-receivable-receipt.sql` | `company_platform`：应收财务字段迁移、收款单与核销明细表及演示数据 |
| `sql/09-finance-invoice-statement.sql` | `company_platform`：手工开票、客户对账单与明细表及演示发票 |
| `sql/03-sharding-schema-shard0.sql` | `company_order0`（3306）：`t_order_0..3`；含旧表补 `contract_id` 的幂等迁移 |
| `sql/04-sharding-schema-shard1.sql` | `company_order1`（3307）：`t_order_0..3`；含旧表补 `contract_id` 的幂等迁移 |
| `sql/10-equipment-scheduling.sql` | `company_order0`：设备台账、设备状态日志、生产工单、设备队列、插队申请、站内消息 6 张表及演示设备 |
| `sql/11-equipment-maintenance.sql` | `company_order0`：设备维护工单、点检保养记录 2 张表及演示点检数据 |
| `sql/12-warehouse.sql` | `company_order0`：仓库、物料、库存、采购入库与跨厂调拨 7 张表及演示仓库物料 |
| `sql/13-material-issue.sql` | `company_order0`：工单领料单与领料明细 2 张表 |
| `sql/14-approval-center.sql` | `company_platform`：审批定义、审批实例、审批任务、审批记录 4 张表及演示审批档位 |
| `sql/15-device-reporting.sql` | `company_order0`：设备凭据、订单作业数据、订单图片、未匹配暂存、接入审计 5 张表及演示设备凭据 |
| `sql/16-expense-loan.sql` | `company_platform`：费用报销、借款、借款还款 3 张表；EXPENSE/LOAN 审批档位沿用 `14-approval-center.sql` |
| `sql/17-ai-assistant.sql` | `company_platform`：AI 对话审计表 `ai_chat_log`，并给全部员工角色授予 `ai:assistant:use` |
| `sql/18-bi-dashboard.sql` | `company_bi`：BI 只读集市（维度/事实/信用占用/指标语义层/ETL 记录 16 张表）、33 条指标口径与 BI 权限码 |
| `sql/05-grants-shard0.sql` / `06-grants-shard1.sql` | 应用账号授权 |

远程 Linux 主机上可执行：

```bash
cd /path/to/company-platform
bash scripts/remote/init-db.sh
bash scripts/remote/verify-db.sh
# 清理冒烟测试与人工验收数据（财务 + 排产 + 两个分片实例的订单）
bash scripts/remote/clean-smoke-data.sh
```

> 若主机重启后 3307 未启动，通常是 `mysql2.service` 的 `ExecStartPre` 以 `mysql` 用户创建 `/run/mysqld2` 失败。安装 `scripts/remote/mysql2-runtime-dirs.conf` 到 `/etc/systemd/system/mysql2.service.d/runtime-dirs.conf`，执行 `systemctl daemon-reload` 后重启服务即可。

### 4.4 Nacos 注册中心

Nacos 3.0.3 独立部署在 `你的IP地址`：

- 服务端：`8848`（HTTP）、`9848/9849`（gRPC）
- 控制台：`http://你的IP地址:8080/`
- 健康检查：`http://你的IP地址:8848/nacos/actuator/health`
- 发行包：`https://download.nacos.io/nacos-server/nacos-server-3.0.3.zip`

远程主机上已把 Nacos 注册为 systemd 服务（`nacos.service`，`wy` 用户运行，随系统启动）：


# 状态 / 重启 / 日志
sudo systemctl status nacos
sudo systemctl restart nacos
sudo journalctl -u nacos -n 100 --no-pager

# 首次安装或更新 unit（仓库内脚本）
cd /path/to/company-platform
bash scripts/remote/install-nacos-service.sh
`$lang

如需临时前台调试，可先停止 systemd 服务，再使用 Nacos 自带脚本：

```bash
cd /home/wy/nacos-3.0.3
sudo systemctl stop nacos
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash bin/startup.sh -m standalone
```

`install-nacos-service.sh` 和 `nacos.service` 的路径按当前主机布局编写：Nacos 位于 `/home/wy/nacos-3.0.3`，JDK 17 位于 `/usr/lib/jvm/java-17-openjdk-amd64`。换机器时同步修改。

开发机上的 `start-all.ps1` 会先检查 Nacos 健康状态，再启动业务服务，并通过 Nacos 服务列表确认 `platform-auth`、`platform-system`、`platform-customer`、`platform-order`、`platform-approval`、`platform-ai`、`platform-bi`、`platform-gateway` 均已注册。

服务注册配置位于各服务的 `application.yml`：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:你的IP地址:8848}
        ip: ${NACOS_CLIENT_IP:你的IP地址}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
        namespace: ${NACOS_NAMESPACE:}
```

- `NACOS_SERVER_ADDR`：Nacos 地址，默认内网地址。
- `NACOS_CLIENT_IP`：注册到 Nacos 的本机地址；默认值 `你的IP地址` 是当前开发机 Wi-Fi 地址。换机器或网络变化时必须改为该机器的内网 IP，或直接设置此环境变量。
- `NACOS_GROUP` / `NACOS_NAMESPACE`：开发和测试默认使用 `DEFAULT_GROUP` 与 public 命名空间。
- `start-all.ps1 -NacosServer http://<host>:8848` 可临时切换注册中心地址。

当前 Nacos 服务端 API 鉴权为关闭状态（仅内网开发），控制台/Admin API 鉴权已开启但尚未初始化控制台账号。生产环境必须开启 `nacos.core.auth.enabled=true`，创建业务账号，并为客户端配置 `spring.cloud.nacos.discovery.username/password`，同时限制 `8848/9848/9849/8080` 的访问来源。当前部署为 standalone + 内嵌 Derby，仅适合开发；生产应改为 Nacos 集群并使用外部 MySQL 持久化。

### 4.5 启动与停止

```powershell
# 构建后启动全部服务（首次或代码变更后加 -Build）
powershell -ExecutionPolicy Bypass -File scripts/dev/start-all.ps1 -Build

# 仅重启
powershell -ExecutionPolicy Bypass -File scripts/dev/start-all.ps1 -Restart

# 查看状态（包含远程 Nacos 健康状态）
powershell -ExecutionPolicy Bypass -File scripts/dev/status.ps1

# 停止本地业务服务（不停止远程 Nacos）
powershell -ExecutionPolicy Bypass -File scripts/dev/stop-all.ps1
```

启动顺序由脚本保证：Nacos 健康检查 → 认证/系统/客户/订单/审批/BI/AI → 网关 → Nacos 服务列表确认。日志和 PID 文件位于 `.run/`。AI 服务健康检查只代表 Spring 进程可用，不代表 Ollama 模型已加载；模型联调见 4.7。`start-all.ps1` 不启动前端，前端使用 `start-web.ps1`。

### 4.6 PC Web 前端启动

前端为独立 Vite 应用，开发端口 `5173`：

```powershell
# 首次运行安装依赖并启动
powershell -ExecutionPolicy Bypass -File scripts/dev/start-web.ps1 -Install

# 日常启动 / 重启
powershell -ExecutionPolicy Bypass -File scripts/dev/start-web.ps1
powershell -ExecutionPolicy Bypass -File scripts/dev/start-web.ps1 -Restart

# 或手动（前端仓库 company-platform-frontend）
cd ../company-platform-web
npm.cmd run dev
```

访问 `http://localhost:5173`，使用认证中心种子账号登录。通过内网 IP 访问时使用 `http://你的IP地址:5173`，认证中心地址会自动推导为 `http://你的IP地址:9000`；该回调地址已注册到开发环境的 `pc-web` 客户端。若更换主机或端口，必须同步更新 `OAuth2ClientConfig` 中的 redirect URI 和 post-logout URI。

前端日志位于 `.run/web.log` / `.run/web.err.log`，`stop-all.ps1` 和 `status.ps1` 已包含前端进程。

### 4.7 本地 Ollama 模型（AI 助手）

AI 助手使用内网服务器 `你的IP地址` 上的 Ollama + Qwen3-4B，避免把业务数据发往云端。服务器无 NVIDIA GPU，当前为 CPU 推理，适合少量管理人员的摘要/问答，不适合高并发。

部署信息：

- Ollama 0.40.8：`/usr/local/bin/ollama`，systemd 服务 `ollama.service`
- 监听：`0.0.0.0:11434`，模型目录 `/home/wy/.ollama/models`
- 关键环境变量：`OLLAMA_CONTEXT_LENGTH=8192`、`OLLAMA_MAX_LOADED_MODELS=1`、`OLLAMA_NUM_PARALLEL=1`、`OLLAMA_KEEP_ALIVE=30m`
- 服务器 4 vCPU / 15 GB，无 NVIDIA GPU、无 swap；模型加载后可用内存约 6 GB，不要同时部署其他大内存服务
- 模型标签：`qwen3:4b-instruct-2507-q4_K_M`（4.0B，Q4_K_M，实际文件 2,497,293,803 字节；Ollama 报告模型元数据上下文 262144，服务实际请求 `num_ctx=8192`，能力 `tools/thinking/completion`；加载后 `ollama ps` 约 3.9 GB 内存，100% CPU 推理）

远程操作：


# 查看服务与日志
sudo systemctl status ollama
sudo journalctl -u ollama -n 100 --no-pager

# 拉取模型（内网首次部署执行一次）
ollama pull qwen3:4b-instruct-2507-q4_K_M
ollama list
ollama ps

# 健康检查
curl -s http://127.0.0.1:11434/api/tags
`$lang

工具调用联调（在任意能访问 `你的IP地址:11434` 的机器执行）：

```bash
curl -s http://你的IP地址:11434/api/chat -H 'Content-Type: application/json' -d '{
  "model": "qwen3:4b-instruct-2507-q4_K_M",
  "stream": false,
  "messages": [
    {"role": "system", "content": "你是公司只读助手，用户问订单时必须调用 get_order_summary，不得编造数据。"},
    {"role": "user", "content": "现在有哪些订单？"}
  ],
  "tools": [{
    "type": "function",
    "function": {
      "name": "get_order_summary",
      "description": "查询当前用户可见的订单总数和最近订单",
      "parameters": {"type": "object", "properties": {}}
    }
  }]
}'
```

2026-10-22 已完成模型拉取和联调：直接请求上述 `/api/chat` 返回 `tool_calls`（`get_order_summary`，空参数对象），说明 Ollama 的工具调用模板可用；首个请求含模型加载约 18 秒，随后同一模型约 6 秒返回工具调用。AgentScope 端到端结果见 7.10.1。

`platform-ai` 默认连接 `http://你的IP地址:11434`，可用环境变量覆盖：

```powershell
$env:OLLAMA_BASE_URL = 'http://你的IP地址:11434'
$env:OLLAMA_MODEL = 'qwen3:4b-instruct-2507-q4_K_M'
```

说明：

- 模型首次加载需要几十秒到数分钟；加载后按 `OLLAMA_KEEP_ALIVE=30m` 常驻，空闲 30 分钟后释放。
- 使用 `-instruct` 标签，默认不输出长思考链；不建议换成普通 `qwen3:4b` 做 CPU 推理，否则响应时间会明显变长。
- CPU 推理单请求可能需要 20–120 秒；前端和网关超时为 300 秒，AI 内部总超时为 240 秒；不要同时发起多个长对话。
- AI 服务只读，不直接连业务库；工具调用会透传当前用户 JWT，由订单、客户、审批服务再次校验权限和数据范围。
- 对话审计写入 `company_platform.ai_chat_log`；审计失败只记日志，不影响回答返回。

### 4.8 冒烟测试

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/smoke-test.ps1
```

该脚本会验证：

1. `ops-client` 客户端凭证令牌签发；
2. 网关到系统、客户、订单、审批、AI 服务的路由；
3. `admin` 的授权码 + PKCE 登录；
4. 用户 JWT 的 `userId`、`username` 声明；
5. 按客户 `9001` 读取已录入合同；
6. 通过网关使用用户令牌创建订单，校验 `contractId`、`createdBy` 写入正确；
7. 按订单号查询详情，验证分片精确路由和合同关联；
8. 调用 `/api/order/orders/page`，验证分页结果和订单号过滤；
9. 调用 `/api/finance/receivables/open`，验证未结清应收查询；
10. 登记 1 分应收，验证客户信用未收金额同步增加；
11. 登记收款并核销，验证应收转为 `PAID`、收款单转为 `ALLOCATED`、信用回到基线；
12. 尝试超额核销，验证服务拒绝并返回 4xx；
13. 登记手工发票，验证发票号码唯一、价税分离、合同可开票额度上限；
14. 推进发票“已寄出 → 客户签收”，验证状态不能回退；
15. 按客户和期间生成对账单，验证期末余额等于应收台账、期初 + 本期应收 - 本期收款 = 期末；
16. 客户确认对账单，验证重复确认和未来期间均被拒绝；
17. 设备上报（第 10 节）：报文信封与一机一密 HMAC-SHA256 验签；错误签名拒绝（2002）、重复序号拒绝（2003）、订单作业数据按订单覆盖、订单图片一条一单（4003）、未匹配订单进暂存（3001）、心跳写入并可从 IoTDB 查询、接入审计与配置接口、销售无权限（403）；CoAP 通道用 UDP PUT 实测回执。MQTT 通道用 `DeviceSimulator` 实测（发布订单数据 → 收到 `server/{deviceNo}/ack` → 数据落 MySQL/审计为 `MQTT`）。
18. 财务单据（第 11 节）：销售提交 500 元/1200 元报销，验证按金额档位分别进入厂长/财务待办、审批意见回写、财务付款与重复付款拦截、超冲销拒绝；驳回后重新提交、撤销审批与取消草稿；借款提交、厂长/财务分档审批、财务放款、销售越权放款/还款拒绝；报销付款冲销借款生成 `OFFSET` 还款记录并联动借款余额；部分还款、超额还款拒绝与全额结清；财务与销售的数据范围（销售读取他人借款 403）、分页与汇总接口。
19. AI 助手（第 7.10 节）：`admin` 登录后通过网关调用 `/api/ai/status`，验证 JWT 中的 `ai:assistant:use`、网关路由和本地模型标签。实际对话依赖 Ollama 模型已加载，属于部署验收，不放进快速冒烟，避免 CPU 推理时间让回归测试不稳定。

注意：冒烟测试会真实写入测试订单、应收/收款/发票/对账单、工单/队列/仓储/领料、报销/借款/还款与设备上报数据（备注或设备编号均带 `smoke test` / `EQ-F001-01` 标记）。重复执行前必须先清理：

```bash
# 3306 实例：业务库与订单分片 0（含设备上报测试数据）
mysql -h127.0.0.1 -P3306 -ucompany_app -p company_platform < scripts/remote/clean-smoke-data.sql
# 3307 实例：订单分片 1
mysql -h127.0.0.1 -P3307 -ucompany_app -p company_order1 < scripts/remote/clean-smoke-orders-3307.sql
```

清理脚本为 `scripts/remote/clean-smoke-data.sql` 与 `clean-smoke-orders-3307.sql`（远程可用 `clean-smoke-data.sh`）。需要清空全部订单测试数据时，可在开发库中对 8 张物理表执行 `TRUNCATE`（生产环境禁止直接清空）。

默认开发账号：

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `Admin@123` | 管理员 |
| `sales01` | `Sales@123` | 销售 |
| `manager01` | `Manager@123` | 厂长/管理 |
| `finance01` | `Finance@123` | 财务 |

生产环境必须修改密码，并替换种子初始化策略。

## 5. OAuth2 / JWT

### 5.1 端点

| 端点 | 用途 |
| --- | --- |
| `http://localhost:9000/.well-known/openid-configuration` | OIDC 发现 |
| `http://localhost:9000/oauth2/jwks` | JWT 公钥 |
| `http://localhost:9000/oauth2/authorize` | 授权码 + PKCE |
| `http://localhost:9000/oauth2/token` | 令牌交换 |
| `http://localhost:9000/login` | 浏览器登录页 |

### 5.2 客户端

| client_id | 认证方式 | Grant | 用途 |
| --- | --- | --- | --- |
| `pc-web` | `none`（公共客户端） | authorization_code（注册了 refresh_token，但 Spring Authorization Server 1.5 默认不向公共客户端签发 refresh token） | PC Web，强制 PKCE；开发回调包括 `http://localhost:5173`、`http://127.0.0.1:5173`、`http://你的IP地址:5173` 下的 `/login/oauth2/code/pc-web` |
| `ops-client` | client_secret_basic/post | client_credentials | 运维/服务间调用，开发密钥 `ops-secret-2026` |

客户端凭证令牌示例：

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=ops-client" \
  -d "client_secret=ops-secret-2026" \
  -d "scope=system.read order.read order.write"
```

### 5.3 JWT 自定义声明

用户令牌包含：

- `user_id`：用户 ID
- `username`：登录名
- `nickname`：昵称（可空，空值不写入）
- `employee_no`：工号（可空）
- `factory_id`：所属工厂（可空）
- `roles`：角色编码数组
- `permissions`：权限编码数组

客户端凭证令牌包含 `client_id`、`username`（等于 client_id）、空角色和按 scope 映射的权限。scope 与权限的映射位于 `PlatformTokenCustomizer`。

### 5.4 浏览器授权码 + PKCE

前端流程：

1. 生成 `code_verifier` 和 `code_challenge`（S256）；
2. 跳转 `/oauth2/authorize`，带 `client_id=pc-web`、`redirect_uri`、`code_challenge`；
3. 用户在 `/login` 登录；
4. 回调页用 `code` + `code_verifier` 调用 `/oauth2/token`；
5. 将 access token 放入 `Authorization: Bearer ...` 请求头。

本地可用 `scripts/dev/smoke-test.ps1` 自动完成整个流程。

## 6. 订单分库分表

### 6.1 物理布局

- `ds_0`：`jdbc:mysql://你的IP地址:3306/company_order0`
- `ds_1`：`jdbc:mysql://你的IP地址:3307/company_order1`
- 每个库：`t_order_0`、`t_order_1`、`t_order_2`、`t_order_3`

### 6.2 路由规则

订单号由雪花算法生成。分片规则：

```text
database = order_no % 2
table    = (order_no / 2) % 4     -- 等价于 (order_no >> 1) % 4
```

注意：不能同时使用 `database = order_no % 2` 和 `table = order_no % 4`。后者会让表序号的奇偶性与库序号完全一致，导致每个库只使用一半物理表（例如 `ds_0` 只落 `t_order_0`/`t_order_2`）。当前规则保证 8 张表都会被使用。

雪花 ID 的 12 位序列在跨毫秒时继续递增，而不是重置为 0。否则订单生成频率不高时，ID 低 12 位长期为 0，所有订单都会落到 `t_order_0`。相关实现见 `common-core/.../SnowflakeIdGenerator.java`，修改前请评估订单号唯一性与分片分布。

### 6.3 查询行为

- 按 `order_no` 查询：精确路由到唯一物理表。
- 按 `customer_id` 查询：`customer_id` 不是分片键，ShardingSphere 会广播到 8 张表后合并。
- 后续如果客户维度查询量增大，应增加客户 ID 与订单号的映射表或索引表，而不是随意增加分片键。

### 6.4 物理验证

在远程 MySQL 上分别查询两个库：

```sql
-- 3306
SELECT 't_order_0' AS t, COUNT(*) FROM company_order0.t_order_0
UNION ALL SELECT 't_order_1', COUNT(*) FROM company_order0.t_order_1
UNION ALL SELECT 't_order_2', COUNT(*) FROM company_order0.t_order_2
UNION ALL SELECT 't_order_3', COUNT(*) FROM company_order0.t_order_3;

-- 3307
SELECT 't_order_0' AS t, COUNT(*) FROM company_order1.t_order_0
UNION ALL SELECT 't_order_1', COUNT(*) FROM company_order1.t_order_1
UNION ALL SELECT 't_order_2', COUNT(*) FROM company_order1.t_order_2
UNION ALL SELECT 't_order_3', COUNT(*) FROM company_order1.t_order_3;
```

## 7. 主要接口

### 7.1 系统服务

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/system/health` | 公开 | 健康检查 |
| GET | `/api/system/me` | 已登录 | 当前 JWT 的用户/角色/权限 |
| GET | `/api/system/users` | `system:user:list` | 用户列表 |
| GET | `/api/system/factories` | `system:factory:list` | 工厂列表 |

### 7.2 客户与合同服务

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/customer/health` | 公开 | 健康检查 |
| GET | `/api/customer/customers/page` | `customer:read` | 分页查询；销售强制只看本人客户 |
| GET | `/api/customer/customers/{id}` | `customer:read` | 客户详情 |
| POST | `/api/customer/customers` | `customer:write` | 新建客户；销售归属强制本人、额度归零 |
| PUT | `/api/customer/customers/{id}` | `customer:write` | 修改非审批字段与状态 |
| GET | `/api/customer/customers/{id}/credit` | `customer:read` | 应收未收、最大逾期天数 |
| GET | `/api/customer/customers/{id}/order-context` | `order:create` | 订单服务内部使用，带数据范围校验 |
| GET | `/api/customer/contracts/page` | `finance:contract:read` | 财务合同分页 |
| POST | `/api/customer/contracts` | `finance:contract:write` | 新建合同，初始“待录入” |
| POST | `/api/customer/contracts/{id}/enter` | `finance:contract:write` | 财务正式录入 |
| GET | `/api/customer/contracts/available?customerId=` | `order:create` | 某客户可下单合同 |
| GET | `/api/customer/contracts/{id}/order-context` | `order:create` | 订单服务内部使用，校验状态/归属/有效期 |

客户与合同规则：

- 客户来源、归属业务员必填；普通销售只能访问本人客户，管理员/财务可访问全部。
- 客户名称、归属、信用额度、结算方式、开票信息属于审批字段，不在普通修改接口中，审批中心上线前需谨慎直接维护。
- 合同金额单位为分；新建合同为 `PENDING_ENTRY`，只有 `ENTERED`/`ACTIVE` 且未过期的合同可被订单引用。
- 信用占用口径固定为：未收应收 + 未关闭订单金额；额度为 0 表示未授信。
- 应收逾期超过 30 天直接阻断下单和发货；当前订单创建已接入阻断，发货校验随仓储/发货模块实现。
- 信用看板只读展示授信、应收、订单占用、可用额度和风险标签；额度调整、临时额度和黑名单恢复必须走审批，审批中心上线前不得由页面直接改数。
- 数据范围：管理员、财务看全部；其他角色（如销售）只看归属自己的客户应收。客户端凭证令牌（机器令牌）没有用户身份，代表组织访问，能访问哪些接口完全由授予它的 OAuth scope 决定（scope 到权限码的映射见 `PlatformTokenCustomizer`）。

### 7.3 财务应收、收款、开票与对账服务

财务接口当前由 `platform-customer` 托管（与信用、合同共用 `company_platform`），对外路径统一为 `/api/finance/**`；后续拆分独立财务服务时只需修改网关路由的 `uri`，前端路径不变。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/finance/receivables/page` | `finance:receivable:read` | 应收分页，附带按筛选条件的汇总指标 |
| GET | `/api/finance/receivables/open?customerId=` | `finance:receivable:read` | 某客户未结清应收，供核销选择 |
| GET | `/api/finance/receivables/{id}` | `finance:receivable:read` | 应收详情 |
| POST | `/api/finance/receivables` | `finance:receivable:write` | 财务手工补录应收，来源标记 `MANUAL` |
| GET | `/api/finance/receipts/page` | `finance:receipt:read` | 收款单分页 |
| GET | `/api/finance/receipts/{id}` | `finance:receipt:read` | 收款详情，含核销明细 |
| POST | `/api/finance/receipts` | `finance:receipt:write` | 登记收款，未核销余额作为预收待认领 |
| POST | `/api/finance/receipts/{id}/allocations` | `finance:receipt:write` | 核销到一张或多张应收 |
| GET | `/api/finance/invoices/page` | `finance:invoice:read` | 手工发票分页，附带开票总额/税额汇总 |
| GET | `/api/finance/invoices/{id}` | `finance:invoice:read` | 发票详情 |
| POST | `/api/finance/invoices` | `finance:invoice:write` | 登记手工发票，价税合计与税额按票面录入 |
| POST | `/api/finance/invoices/{id}/send` | `finance:invoice:write` | 标记发票已寄出（`REGISTERED → SENT`） |
| POST | `/api/finance/invoices/{id}/sign` | `finance:invoice:write` | 标记客户已签收（`SENT → SIGNED`） |
| GET | `/api/finance/statements/page` | `finance:statement:read` | 客户对账单分页，期间按区间交集筛选 |
| GET | `/api/finance/statements/{id}` | `finance:statement:read` | 对账单详情，含生成时固化的明细 |
| POST | `/api/finance/statements` | `finance:statement:write` | 按客户和期间生成对账快照 |
| POST | `/api/finance/statements/{id}/confirm` | `finance:statement:write` | 客户确认归档，可记录差异说明 |

应收与收款规则：

- 金额一律以分存储；核销前后端都用整数比较，不做四舍五入。
- 账龄与逾期不落库，按“未收余额 + 到期日”实时计算；逾期不是应收状态。
- 一张收款单可核销多张应收，一张应收也可分多次收款；核销总额不得超过收款单可用余额，单张应收核销额不得超过其未收余额。
- 核销在事务内先锁收款单，再按应收 ID 升序加锁，数据库层再校验一次余额，避免并发超收。
- 核销明细只追加不修改、不物理删除；反核销/红冲必须走审批，审批中心上线前不提供作废接口。
- 应收作废同样不提供物理删除；当前阶段的正式应收由发货/开票自动生成，页面只提供补录入口。
- 收款登记只写 `company_platform`，不对接银行、第三方支付或电子回单。
- 手工开票只做内部登记，不对接税控、电子发票平台或总账；发票号码全局唯一，价税合计和税额按票面录入，系统不替用户做价税换算。
- 开票金额不得超过合同剩余可开票额度（合同金额 - 已登记有效发票金额）；关联合同必须属于同一客户且为“已录入/生效”。
- 发票状态只允许 `REGISTERED → SENT → SIGNED` 顺序推进；作废/红冲必须关联原发票并走审批，审批中心上线前不提供接口。
- 对账单按客户和期间生成快照：期初 = 期初前应收 - 期初前核销；期末 = 期初 + 本期应收 - 本期核销收款。本期开票金额仅作参考，不冲减应收；未核销预收不进入对账单。
- 已取消应收、已作废/红冲发票不进入对账金额；对账单生成后不可修改，重新生成保留新版本，客户确认后归档。
- 权限分配：销售只有 `finance:receivable:read`（只看归属自己的客户应收）；收款核销、手工开票和对账单（`finance:receipt:write`、`finance:invoice:read/write`、`finance:statement:read/write`）只授予财务和管理员。

### 7.4 订单服务

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/order/orders/health` | 公开 | 健康检查 |
| POST | `/api/order/orders` | `order:create` | 创建订单；必须关联已录入合同 |
| GET | `/api/order/orders/{orderNo}` | `order:read` | 按订单号查询，受数据范围限制 |
| GET | `/api/order/orders/page` | `order:read` | 分页查询，支持订单号/客户/工厂/状态，`size` 上限 100 |
| GET | `/api/order/orders/occupancy?customerId=...` | `finance:credit:read` | 客户未关闭订单金额/数量，供信用看板合并应收 |
| GET | `/api/order/orders?customerId=...` | `order:read` | 按客户查询（广播），受数据范围过滤 |

创建订单请求：

```json
{
  "customerId": 9001,
  "contractId": 7001,
  "factoryId": 1,
  "totalAmount": 123456,
  "currency": "CNY",
  "remark": "example"
}
```

`totalAmount` 单位为分，必须为正整数；金额一律使用 `BIGINT` 存储，不做四舍五入。服务端会通过客户服务校验客户状态、合同状态/归属/有效期/币种/剩余额度、信用额度与逾期天数，任一失败都不会写入订单。客户服务不可用时按失败关闭处理。

订单数据范围：管理员/财务可看全部；厂长只看本厂；其他角色当前按创建人隔离。客户归属业务员维度将在后续订单补全业务员字段后切换。

### 7.5 设备台账与生产排产服务

排产相关表固定在 `company_order0`（ShardingSphere `SingleRuleConfiguration`），与分片订单表物理隔离，避免跨库事务；订单表与工单表的状态同步目前是尽力而为，生产环境需要改为可靠事件。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/order/devices/page` | `equipment:read` | 设备台账分页，附带当前工单、队列长度和预计空闲时间 |
| GET | `/api/order/devices/options?factoryId=` | `equipment:read` 或 `production:schedule:read` | 排产用设备下拉（不含停用设备） |
| GET | `/api/order/devices/{id}` | `equipment:read` | 设备详情 |
| POST | `/api/order/devices` | `equipment:write` | 新建设备；编号全公司唯一，归属单一工厂 |
| PUT | `/api/order/devices/{id}` | `equipment:write` | 修改能力、位置等信息；编号与工厂不可改 |
| POST | `/api/order/devices/{id}/status` | `equipment:write` | 人工变更为空闲/保养/故障/停用；生产中不允许停机 |
| GET | `/api/order/production/board?factoryId=` | `production:schedule:read` | 排产看板：按设备分组返回当前工单与等待队列 |
| GET | `/api/order/work-orders/page` | `production:schedule:read` | 工单分页，支持订单号/工厂/状态筛选 |
| GET | `/api/order/work-orders/{id}` | `production:schedule:read` | 工单详情 |
| GET | `/api/order/work-orders/by-order/{orderNo}` | `production:schedule:read` | 某订单的全部工单（订单详情页使用） |
| POST | `/api/order/work-orders` | `production:schedule:write` | 创建工单；可带设备直接入队，不带设备进入待排产 |
| POST | `/api/order/work-orders/{id}/assign` | `production:schedule:write` | 给待排产工单分配同工厂设备 |
| POST | `/api/order/work-orders/{id}/start` | `production:schedule:write` | 开始队首工单，设备转生产中 |
| POST | `/api/order/work-orders/{id}/complete` | `production:schedule:write` | 完成工单，释放设备并顺延队列 |
| POST | `/api/order/work-orders/{id}/cancel` | `production:schedule:write` | 取消待排产/排队中的工单（生产中不可取消） |
| POST | `/api/order/work-orders/{id}/jump-requests` | `production:queue:jump:apply` | 申请插队；只能对自己排队中的工单申请，同时提交审批中心 |
| GET | `/api/order/jump-requests/page` | `production:queue:jump:apply` 或 `production:queue:jump:approve` | 插队申请分页；销售只看自己发起的申请 |
| GET | `/api/order/jump-requests/{id}` | `production:queue:jump:apply` 或 `production:queue:jump:approve` | 插队申请详情 |
| POST | `/api/order/jump-requests/{id}/apply-approval` | `approval:apply` | 审批中心回调：审批通过后重排队列并通知被顺延业务员 |
| POST | `/api/order/jump-requests/{id}/reject-approval` | `approval:approve` | 审批中心回调：驳回并同步申请状态 |
| POST | `/api/order/jump-requests/{id}/cancel-approval` | `approval:submit` | 审批中心回调：申请人撤销并同步申请状态 |
| GET | `/api/order/messages/page` | 已登录 | 站内消息分页（只返回本人消息） |
| GET | `/api/order/messages/unread-count` | 已登录 | 未读消息数，供顶栏角标轮询 |
| POST | `/api/order/messages/{id}/read` | 已登录 | 标记单条消息已读 |
| POST | `/api/order/messages/read-all` | 已登录 | 全部标记已读 |
| GET | `/api/order/maintenance-orders/page` | `equipment:maintenance:read` | 维护工单分页，附带状态与成本汇总 |
| GET | `/api/order/maintenance-orders/{id}` | `equipment:maintenance:read` | 维护工单详情 |
| GET | `/api/order/maintenance-orders/by-device/{deviceId}` | `equipment:maintenance:read` | 某台设备的维护历史 |
| POST | `/api/order/maintenance-orders` | `equipment:maintenance:write` | 新建维护工单；设备状态保持不变 |
| POST | `/api/order/maintenance-orders/{id}/start` | `equipment:maintenance:write` | 开始维护；设备转为保养或故障，等待队列保留 |
| POST | `/api/order/maintenance-orders/{id}/complete` | `equipment:maintenance:write` | 完成维护；登记成本与停机时长，设备恢复空闲 |
| POST | `/api/order/maintenance-orders/{id}/cancel` | `equipment:maintenance:write` | 取消待处理的维护工单 |
| GET | `/api/order/inspections/page` | `equipment:inspection:read` | 点检记录分页，附带正常/异常统计 |
| POST | `/api/order/inspections` | `equipment:inspection:write` | 登记点检记录 |
| POST | `/api/order/inspections/{id}/maintenance-order` | `equipment:maintenance:write` | 由异常点检生成“点检整改”维护工单 |

排产规则：

- 订单先按材料/工艺/长度拆成工单；工单固定跟随订单的工厂，**不能跨厂排产**，订单可在同厂多台设备之间拆分。
- 设备匹配按材料、门幅、工艺、产能和状态校验；`RUNNING` 状态不能人工设置，只能由开始工单进入。
- 一台设备同一时间只执行一个工单，超出产能的工单按进入队列顺序 FIFO 等待，队列位置从 1 开始。
- 预计生产分钟数 = 计划米数 ÷ 设备产能（米/小时）× 60，向上取整到分钟，再加换型时间；计划长度保留 2 位小数并截断。
- 订单状态由工单联动：全部工单排队为 `QUEUED`，任一工单开始为 `IN_PRODUCTION`，全部工单完成为 `COMPLETED`。
- 设备保养/故障/停机时等待队列保留，并给受影响业务员发送交期风险消息；生产中的工单必须先完成。
- 插队必须由销售对自己排队中的工单发起申请，厂长审批；通过后整体重排等待队列，被顺延业务员和申请人都会收到站内消息，**不做经济补偿**。审批已迁入统一审批中心：申请创建时同步提交审批单，审批人在「我的待办」处理，订单服务只保留业务单据与三个审批回调接口。
- 责任业务员按订单创建人认定，与订单数据范围保持一致；销售在排产看板只能看到自己工单对应的队列记录，看不到同事的客户和订单。
- 插队审批目前是过渡实现（独立端点 + 独立页面），审批中心上线后迁移为统一待办。

维护与点检规则：

- 维护工单是设备停机保养的唯一业务凭据：新建不影响排产；开始维护时设备转为 `MAINTENANCE`（故障维修为 `FAULT`），等待队列保留原顺序，受影响业务员收到交期风险消息。
- 生产中的设备不能停机，必须先完成当前工单；保养/维修期间设备不接受新工单入队。
- 维护完成后设备恢复 `IDLE`，队列顺序不变但计划时间按新的空闲时间重算，等待中的业务员收到恢复通知。
- 维护费用**全部由公司承担**，只做成本记录，不生成应收、不向客户收取，也不计算设备使用费；金额以分存储。
- 停机时长不传时按实际开始到结束时间自动计算；点检记录只登记检查结论，不修改设备状态。
- 点检结果为异常时必须填写异常项数，且异常项数不能超过点检项数；异常点检可一键生成“点检整改”维护工单，工单回填点检记录 ID 便于追溯。
- 权限：厂长可处理本厂设备的维护与点检，管理员可跨厂；操作员只登记点检；销售和财务只读查看维护情况（用于交期判断与成本了解）。

### 7.6 仓储服务

仓储表与设备、排产表同在 `company_order0`（ShardingSphere 单表规则固定 `ds_0`），便于后续工单领料直接关联工单；未来若拆分为独立 `platform-warehouse` 服务，只需迁移表与网关路由。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/order/warehouses/page` | `warehouse:read` | 仓库分页，附带库存概览 |
| GET | `/api/order/warehouses/options` | `warehouse:read` / `warehouse:inbound` / `warehouse:outbound` | 仓库下拉选项（只含启用仓库） |
| GET | `/api/order/warehouses/{id}` | `warehouse:read` | 仓库详情 |
| POST | `/api/order/warehouses` | `warehouse:manage` | 新建仓库；每厂只能有一个启用主仓 |
| PUT | `/api/order/warehouses/{id}` | `warehouse:manage` | 修改仓库；编号与所属工厂不可改 |
| POST | `/api/order/warehouses/{id}/status` | `warehouse:manage` | 启用/停用仓库；仍有库存不允许停用 |
| GET | `/api/order/materials/page` | `warehouse:read` | 物料分页，附带库存合计 |
| GET | `/api/order/materials/options` | `warehouse:read` / `warehouse:inbound` / `warehouse:outbound` | 物料下拉选项（只含启用物料） |
| GET | `/api/order/materials/{id}` | `warehouse:read` | 物料详情 |
| POST | `/api/order/materials` | `warehouse:manage` | 新建物料；编码全公司唯一 |
| PUT | `/api/order/materials/{id}` | `warehouse:manage` | 修改物料；编码不可改 |
| POST | `/api/order/materials/{id}/status` | `warehouse:manage` | 启用/停用物料；仍有库存不允许停用 |
| GET | `/api/order/inventories/page` | `warehouse:read` | 库存台账分页，可按仓库/物料/关键字过滤 |
| GET | `/api/order/purchase-receipts/page` | `warehouse:read` | 采购入库单分页，附带状态与金额汇总 |
| GET | `/api/order/purchase-receipts/{id}` | `warehouse:read` | 入库单详情（含明细） |
| POST | `/api/order/purchase-receipts` | `warehouse:inbound` | 新建采购入库单（草稿） |
| POST | `/api/order/purchase-receipts/{id}/receive` | `warehouse:inbound` | 确认收货：增加库存并锁定金额 |
| POST | `/api/order/purchase-receipts/{id}/cancel` | `warehouse:inbound` | 取消草稿入库单 |
| GET | `/api/order/stock-transfers/page` | `warehouse:read` | 调拨单分页，附带待出库/在途/已入库统计 |
| GET | `/api/order/stock-transfers/{id}` | `warehouse:read` | 调拨单详情（含明细） |
| POST | `/api/order/stock-transfers` | `warehouse:outbound` | 新建调拨单（待出库） |
| POST | `/api/order/stock-transfers/{id}/ship` | `warehouse:outbound` | 出库：扣减调出仓库库存 |
| POST | `/api/order/stock-transfers/{id}/receive` | `warehouse:inbound` | 入库：增加调入仓库库存 |
| POST | `/api/order/stock-transfers/{id}/cancel` | `warehouse:outbound` | 取消待出库调拨单 |
| GET | `/api/order/material-issues/page` | `warehouse:read` | 工单领料单分页，附带待发料/已发料与领料金额汇总 |
| GET | `/api/order/material-issues/{id}` | `warehouse:read` | 领料单详情（含明细） |
| POST | `/api/order/work-orders/{workOrderId}/issues` | `warehouse:outbound` | 为工单新建领料单（待发料，不扣库存） |
| POST | `/api/order/material-issues/{id}/issue` | `warehouse:outbound` | 确认发料：扣减库存并计入工单材料成本 |
| POST | `/api/order/material-issues/{id}/cancel` | `warehouse:outbound` | 取消待发料领料单 |

仓储规则：

- 每个工厂至少保留一个启用主仓，仓库不跨厂；跨厂移库只能走调拨单，同厂调拨用于车间内部移库。
- 物料编码全公司唯一且不可修改；单价最多 4 位小数、损耗率 2 位小数（0–100），数量统一 2 位小数并截断。
- **采购入库是库存增加与材料成本的唯一来源**：入库单先建草稿，确认收货时才按“数量 × 单价”截断到分并增加库存；草稿不影响库存。
- 没有期初库存，也不提供库存调整接口；库存只能由采购入库和调拨入库产生，出库只能通过调拨（后续是工单领料）。
- 库存数量不允许为负：出库用条件更新扣减，库存不足时整单失败并保持来源库存不变。
- 已收货的入库单是材料成本的既成事实，不提供作废/红冲（审批中心上线前）；跨厂调拨的出库与入库由两边工厂分别确认。
- 权限：厂长可维护本厂仓库物料并完成出入库，管理员可跨厂；操作员可出库（领料），财务只读查看采购金额。
- 工单领料把仓库与生产工单连起来：领料单先建单（待发料，不扣库存），确认发料才扣减库存并把金额计入工单材料成本；领料仓库必须与工单同厂，工单需处于排队中或生产中。
- 工单材料成本不落库，由已发料领料单明细实时汇总（分），避免与领料单两处维护；成本属于内部信息，只有拥有 `production:cost:read` 的角色才能在工单视图看到该字段。

### 7.7 流程审批中心

审批中心是独立服务 `platform-approval`（端口 9104），数据保存在 `company_platform`，对外路径 `/api/approval/**`。它只负责流程与结论，具体业务变更由业务动作执行器回调对应服务落地。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/approval/instances/page` | `approval:read` | 审批单分页，`mine=true` 只看本人提交的申请 |
| GET | `/api/approval/instances/{id}` | `approval:read` | 审批单详情（含任务与轨迹） |
| POST | `/api/approval/instances` | `approval:submit` | 提交审批申请；按业务类型与金额匹配审批档位 |
| GET | `/api/approval/todos/page` | `approval:approve` | 我的待办分页（按指定审批人或审批角色命中） |
| POST | `/api/approval/tasks/{taskId}/approve` | `approval:approve` | 审批同意；业务动作先执行，失败整体回滚 |
| POST | `/api/approval/tasks/{taskId}/reject` | `approval:approve` | 审批驳回；必须填写意见 |
| POST | `/api/approval/instances/{id}/cancel` | `approval:submit` | 申请人撤销待审批申请 |
| GET | `/api/approval/definitions/page` | `approval:read` | 审批流程与金额档位分页 |
| POST | `/api/approval/definitions` | `approval:manage` | 新建审批流程（档位） |
| PUT | `/api/approval/definitions/{id}` | `approval:manage` | 修改审批流程 |
| POST | `/api/approval/definitions/{id}/status` | `approval:manage` | 启用/停用审批流程 |

审批规则：

- 审批定义按“业务类型 + 金额区间”匹配：下限含、上限不含，上限为空表示不封顶；同一业务类型可配置多档，**优先级数字小的先命中**。金额以分存储。
- 未配置审批流程的业务类型不允许提交申请，避免出现无人审批的单据；审批档位由财务在系统上线前配置。
- 审批通过时**先执行业务动作再落结论**：业务落地失败会回滚整个审批事务，实例保持待审批，不会出现“审批通过但业务未变更”。
- 业务动作由 `ApprovalActionHandler` 扩展实现，当前已接入四类：客户信用额度调整（审批通过后调用客户服务落地额度）、设备队列插队（审批通过后调用订单服务重排队列并通知被顺延业务员；驳回与撤销只同步申请状态）、费用报销（通过后进入待付款，驳回/撤销同步单据状态）、借款（通过后进入待放款，驳回/撤销同步单据状态）。
- 待办命中条件为“任务指定本人”或“任务的审批角色在本人角色列表中”；管理员是兜底审批人，可以看到并处理全部待办。
- 审批轨迹只追加不修改，保留提交、同意、驳回与撤销的完整记录；申请人不等于审批人时，申请人可撤销自己的待审批申请。
- 权限：销售可提交申请并查看自己的申请；财务与厂长可审批；财务可维护审批档位；`approval:apply` 用于执行审批通过后的业务变更。

### 7.8 设备数据上报

设备上报是 V1.4 的最后一个 P0 模块，代码位于 `platform-order`，数据分两条线：**订单作业数据落 MySQL**（`company_order0`，按订单号唯一、后推覆盖），**自检/异常/心跳落 Apache IoTDB**（时序数据）。上行入口有三条，共用同一套验签、去重、路由与落库逻辑：

| 通道 | 入口 | 说明 |
| --- | --- | --- |
| MQTT（首选） | `device/{deviceNo}/order`、`/image`、`/status`、`/selfcheck`、`/fault` | 服务端内置 MQTT 3.1.1 订阅客户端（QoS 0/1、PINGREQ 保活、断线重连），回执发到 `server/{deviceNo}/ack` |
| CoAP | `PUT /device/{deviceNo}/{类型}`，UDP 5683 | 内置最小 CoAP 服务（RFC 7252 子集），回执为带 JSON 载荷的 ACK |
| HTTP（联调/兜底） | `POST /api/iot/report` | 不经 OAuth 令牌，设备用一机一密密钥验签 |

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/iot/report` | 设备签名 | 设备上行入口；回执码 0 成功、2002 签名错误、2003 重放/重复、3001 订单未匹配等 |
| GET | `/api/order/device-reports/page` | `production:report:read` | 订单作业数据分页（按订单号覆盖） |
| GET | `/api/order/device-reports/images/page` | `production:report:read` | 订单图片记录分页 |
| GET | `/api/order/device-reports/staging/page` | `production:report:read` | 未匹配暂存分页 |
| GET | `/api/order/device-reports/logs/page` | `production:report:read` | 接入审计分页（含回执码） |
| GET | `/api/order/device-reports/credentials/page` | `production:report:read` | 设备凭据分页（密钥掩码） |
| GET | `/api/order/device-reports/iotdb/{deviceNo}` | `production:report:read` | 查询 IoTDB 测点时序数据 |
| GET | `/api/order/device-reports/config` | `production:report:read` | 接入配置概览（初始化默认值） |

上报规则：

- 报文为统一 JSON 信封：`version`、`msgType`、`deviceNo`、`orderNo`、`timestamp`、`seq`、`nonce`、`sign`、`payload`。签名算法 `HMAC-SHA256(secret, deviceNo|msgType|timestamp|nonce|payload原文)`，输出十六进制小写；**验签使用报文里的载荷原文**，服务端不重新序列化，避免数字格式差异导致签名不一致。
- 设备一机一密：密钥保存在 `t_device_credential`（种子数据含演示设备 `EQ-F001-01`），未登记或已停用的设备返回 2004；时间戳偏差超过重放窗口返回 2003；`deviceNo + seq` 在去重窗口内重复返回 2003。
- 订单作业数据按 `order_no` 唯一：同一订单每 10 秒上报一次，**最新一次覆盖历史**，不保留快照；长度类字段保留 3 位小数（设备口径），完成百分比保留 2 位小数并截断。
- 订单图片一条订单**只接受一次**：重复上传返回 4003；图片大小超过配置上限或格式不在白名单返回 4002；订单号无法精确匹配返回 4001。
- 订单号精确匹配：去掉空格/换行 → 可选去掉前缀 → 可选正则校验 → 解析为数字后查订单。匹配失败的数据落 `t_device_report_staging` 暂存并返回 3001，匹配成功后自动归位并清理暂存。
- 自检、异常/故障与心跳写 IoTDB：路径为 `root.device.device_{设备编号}`，测点包括 `temperature`、`ink_level_percent`、`work_state`、`fault_code`、`fault_level` 等；写入失败只记日志，不影响设备上报主流程。
- 每条上行报文都写 `t_device_access_log`（接入方式、设备序号、回执码、是否匹配），供联调与排障。
- 所有参数（Broker 地址、Topic 模板、CoAP 端口、签名与重放窗口、图片限制、OCR 阈值、IoTDB 地址、上报频率与保留周期）都在配置文件 `iot.*` 中，初始化默认值见 `config/application-iot.yml` 与 `platform-order/src/main/resources/application.yml`，现场改配置即可。
- 联调工具：`com.company.platform.order.simulator.DeviceSimulator` 可按信封格式构造报文、计算签名、发布 MQTT 并打印平台回执。

### 7.9 财务单据（费用报销与借款/还款）

费用报销与借款/还款是 V1.4 财务模块的员工单据，代码位于 `platform-customer`，接口仍走网关 `/api/finance/**`。审批统一提交到 `platform-approval`，审批中心通过 `ApprovalActionHandler` 回调客户服务落地状态。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/finance/expenses` | `finance:expense:write` | 新建报销草稿（报销人取当前用户） |
| POST | `/api/finance/expenses/{id}/submit` | `finance:expense:write` | 提交审批，按金额档位路由 |
| POST | `/api/finance/expenses/{id}/cancel` | `finance:expense:write` | 取消草稿；审批中则同步撤销审批 |
| POST | `/api/finance/expenses/{id}/pay` | `finance:expense:pay` | 财务付款，可用未还借款冲销 |
| GET | `/api/finance/expenses/{id}/outstanding-loans` | `finance:expense:pay` | 报销人可冲销的未还借款 |
| GET | `/api/finance/expenses/page`、`/{id}` | `finance:expense:read` | 分页与详情 |
| POST | `/api/finance/loans` | `finance:loan:write` | 新建借款草稿 |
| POST | `/api/finance/loans/{id}/submit` | `finance:loan:write` | 提交审批（低于 5000 元厂长，5000 元及以上财务） |
| POST | `/api/finance/loans/{id}/disburse` | `finance:loan:disburse` | 财务放款 |
| POST | `/api/finance/loans/{id}/repay` | `finance:loan:repay` | 财务登记手工还款 |
| GET | `/api/finance/loans/page`、`/{id}` | `finance:loan:read` | 分页与详情（含还款记录） |

规则：

- 金额统一按分存储；页面录入的元最多两位小数并直接截断，不做四舍五入。借款人与报销人取当前登录用户，不接受前端指定。
- 报销状态：草稿 → 审批中 → 待付款 → 已付款；驳回、撤销保留单据，状态分别为已驳回、已取消。低于 1000 元由厂长审批，1000 元及以上由财务审批。审批通过只代表可付款，财务登记付款后才算结清。
- 借款状态：草稿 → 审批中 → 待放款 → 还款中 → 已还清；借款与报销互相独立。低于 5000 元由厂长审批，5000 元及以上由财务审批。审批通过后仍需财务实际放款。
- 报销付款可以冲销报销人的未还借款：冲销金额 + 现金实付 = 报销金额；冲销生成一条 `OFFSET` 还款记录并关联报销单号，不产生现金流水。借款人必须与报销人一致，冲销金额不能超过借款未还余额。
- 审批回调携带审批人 JWT，落地失败会让审批事务整体回滚，避免“审批通过但单据状态未变”。回调幂等，重复通知不会改坏状态。
- 撤销回调用 `approval:apply` 或 `approval:submit` 放行；服务层对没有 `approval:apply` 的调用者强制校验单据归属，员工只能撤销本人单据，防止越权。
- 单据不物理删除；经济业务错误在当前版本只能通过状态流转控制，红冲能力随发票作废/红冲一并补齐。
- 编号：报销 `BX-<雪花>`、借款 `JK-<雪花>`、还款 `HK-<雪花>`，共用客户服务内的雪花 ID 生成器（`app.snowflake.*` 可配置机器号）。
- 权限与数据范围：所有员工可查看/新建/提交本人报销与借款；付款、放款、还款仅财务（ADMIN 兜底）。财务/管理员/机器令牌看全部，厂长看本厂或本人，其他员工只看本人。

### 7.10 AI 助手（platform-ai，只读）

`platform-ai` 监听 `9106`，网关路径 `/api/ai/**`，权限码 `ai:assistant:use`（所有员工角色和 ADMIN 已授予）。服务使用 AgentScope 2.0.4 的 `ReActAgent` 管理多轮对话和工具调用，模型走本地 Ollama 原生 `/api/chat`（`OllamaChatModel`），不直连业务数据库。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/ai/health` | 放行 | 服务健康检查 |
| GET | `/api/ai/status` | `ai:assistant:use` | 当前模型标签和只读说明 |
| POST | `/api/ai/chat` | `ai:assistant:use` | 对话，body：`{"message":"...","sessionId":"..."}` |

第一阶段工具全部无参数，降低 4B 模型生成错误参数的概率：

| 工具 | 下游接口 | 需要权限 | 返回内容 |
| --- | --- | --- | --- |
| `get_order_summary` | `/api/order/orders/page` | `order:read` | 订单总数、最近 8 笔、样本合计 |
| `get_receivable_summary` | `/api/finance/receivables/page` | `finance:receivable:read` | 应收/已收/未收/逾期汇总、最近逾期明细 |
| `get_expense_summary` | `/api/finance/expenses/page` | `finance:expense:read` | 报销状态与金额汇总、最近报销单 |
| `get_loan_summary` | `/api/finance/loans/page` | `finance:loan:read` | 借款申请总额、已放款未还、累计已还、最近借款单 |
| `get_approval_todo_summary` | `/api/approval/todos/page` | `approval:approve` | 待办数量与最近待办 |

规则：

- AI 只读。不提供审批、驳回、付款、放款、还款、改信用额度、改排产等写工具；用户要求这些动作时，回答会引导回原业务页面。
- 工具调用透传当前用户 JWT，目标服务再次执行权限与数据范围校验。AI 不是超级用户，销售看不到他人客户/借款，财务/管理员的可见范围也与页面一致。
- 模型只看到工具返回的文本摘要，不接触生产库、SQL 或完整明细；没有调用工具时不允许编造业务数据。
- 会话状态按 `userId/sessionId` 保存在 `./data/ai-sessions`，服务重启后可继续；该目录是明文 JSON，包含对话内容，生产需限制权限并设置保留期，测试后可直接删除。前端“新会话”会生成新的 `sessionId`，避免 8K 上下文被长期对话撑满。
- 每次对话写入 `ai_chat_log`（用户、会话、问题、回答、工具轨迹、模型、耗时、状态）。审计写入失败只记日志，不影响回答。
- 当前不支持流式输出、语音、文件上传、写操作或跨用户共享会话。首页大屏/BI 已由独立的 `platform-bi` 服务实现（见 7.11），NL2SQL 仍未实现；本服务不承接图表与大屏聚合。

#### 7.10.1 本地 4B 验收结果（2026-10-22）

环境：Ollama 0.40.8，`qwen3:4b-instruct-2507-q4_K_M`，4 vCPU / 15 GB，纯 CPU 推理，`OLLAMA_NUM_PARALLEL=1`。

已验证：

- AgentScope `ReActAgent` + Ollama 原生工具调用可用：5 个无参数工具均能被 Qwen 4B 正确选择并执行；多轮会话按 `userId/sessionId` 持久化，追问“我刚才问了你什么”可准确回忆。
- 数据范围：在含冒烟订单的数据集下，同一句“最近订单情况”中，`admin` 看到 12 笔订单，`sales01` 只看到本人范围内的 0 笔；`sales01` 查询审批待办时下游返回 403，AI 明确回答无权限，没有绕过目标服务校验。
- 业务摘要：订单、应收/逾期、报销、借款、审批待办均返回真实下游数据；审批待办测试中正确列出 1 条 500 元差旅报销，并提示 AI 只读。
- 审计：`ai_chat_log` 正常记录问题、回答、工具轨迹、模型、耗时和状态；浏览器发起的会话也能落审计。
- 前端：`/assistant` 的登录跳转、侧边栏入口、示例问法、加载态、多行中文回答和工具标签均正常；成功请求没有新增 console error（模型未就绪时的旧 503 属预期错误）。
- 性能（同一台 4 vCPU 服务器）：直接 Ollama 首个请求含模型加载约 18 秒；AgentScope 冷启动订单查询约 75 秒，预热后订单查询 33–48 秒，审批/多工具查询 19–45 秒，无工具追问约 4 秒，均在 300 秒超时内。
- 回归：`mvn test` 通过，前端 `npm run build` 通过，完整冒烟 **362 PASS / 0 FAIL**（含 2 条 AI 状态断言）。

限制：

- 单模型、单并发；多人同时长对话会排队，CPU 推理不适合作为高并发生产问答。
- 4B 模型可能把“最近 8 笔样本”概括成总体，或对总额/状态做不精确归纳；财务金额必须回到业务页面复核，AI 输出不能作为审批、付款或信用决策依据。
- 当前只有 5 个固定摘要工具，不支持任意筛选、明细导出、NL2SQL、图表或写操作；扩展时优先增加只读工具/指标语义层，不要向模型开放原始生产 SQL。
- 会话状态是本地 JSON 文件（`./data/ai-sessions`），包含对话内容；生产需限制目录权限、设置保留期和清理策略。
- Ollama `11434` 无认证，必须限制来源；`platform-ai` 的 JWT 透传、只读工具白名单和审计不可关闭。

### 7.11 首页大屏与管理驾驶舱（platform-bi，只读）

`platform-bi` 监听 `9105`，网关路径 `/api/bi/**`。它不承接任何业务写入，也不复用业务接口，而是**自己只读抽取业务库、写入独立的 BI 集市 `company_bi`，再对外提供聚合查询**。这样看板查询永远不会拖慢在线业务，也保证“看板看到的口径”和“业务页面写下的数据”来自同一份快照。

#### 7.11.1 数据链路

```text
company_platform(3306)  ┐
company_order0(3306)   ├─► platform-bi ─► company_bi(3306) ─► /api/bi/dashboard
company_order1(3307)   ┘   (只读 ETL)      (只读事实表)        /api/bi/metrics
```

- 抽取：`BiSnapshotExtractor` 分三条只读连接读取业务表，在内存里补齐跨库关系（订单→工厂、订单→业务员、客户→常用工厂），不写任何业务表。
- 写入：`BiSnapshotWriter` 在**单个本地事务**里先 `DELETE` 再批量 `INSERT` 事实表；InnoDB 的 MVCC 保证未提交前查询方仍看到上一版数据，提交后一次性切换到新快照，页面不会出现空表或半批数据。
- 调度：默认每 **5 分钟**全量刷新一次（`app.bi.refresh-ms`），启动后延迟 15 秒首次执行（`app.bi.initial-delay-ms`）。同一时刻只允许一个 ETL，上一轮未结束则跳过本轮。
- 失败处理：抽取或写入失败只记录到 `bi_etl_run`（`status=FAILED` + 原因），**旧快照保持可读**，页面显示“数据刷新延迟”而不是报错堆栈。
- 时间戳：ETL 运行的 `started_at/finished_at` 由应用本地时钟写入（与平台其它业务表一致）。数据库 `NOW()` 走数据库服务器时区，混用会让“最后同步时间”偏差 8 小时。
- 金额：事实表全部以**分（BIGINT）**存储；比例由看板服务按明确分子/分母实时计算并保留两位小数；金额展示统一由后端按分转元并**直接截断**，前端不做金额除法。

#### 7.11.2 数据集市

`sql/18-bi-dashboard.sql` 创建 `company_bi`（16 张表）：

| 表 | 用途 |
| --- | --- |
| `bi_dim_factory` / `bi_dim_customer` | 工厂与客户维度（含归属业务员、常用工厂、信用额度） |
| `bi_fact_order` | 订单事实（金额、状态、工厂、归属人、创建/完成时间） |
| `bi_fact_receivable` / `bi_fact_receipt` | 应收（含逾期标记与逾期天数）与收款事实 |
| `bi_fact_expense` / `bi_fact_loan` | 费用报销与借款事实 |
| `bi_fact_work_order` / `bi_fact_queue` / `bi_fact_equipment` | 工单、设备队列与设备当前状态快照 |
| `bi_fact_inventory` / `bi_fact_transfer` | 库存快照（含最近采购入库单价）与跨厂调拨事实 |
| `bi_fact_approval` | 审批实例事实（含耗时秒数） |
| `bi_customer_credit` | 客户信用占用（未收应收 + 未关闭订单） |
| `bi_metric` | **指标语义层**：口径、分类、单位、数据源、`READY`/`PENDING_SOURCE` |
| `bi_etl_run` | ETL 运行记录（开始/结束时间、状态、写入行数、失败原因） |

#### 7.11.3 指标语义层与“待数据源”

`bi_metric` 是 V1.4 第 11 章 KPI 清单的落地，共 33 条：

- **17 条 `READY`**：本月订单额/订单数、订单完成率、本月收款、应收余额、逾期应收、信用超限客户、未关闭订单、故障设备、队列平均等待、库存金额、缺料预警项、调拨在途、待付款报销、借款未还、待审批数量、平均审批时长。
- **16 条 `PENDING_SOURCE`**：准时交付率、报价转化率、订单变更率、返工率、产量、设备利用率、设备故障率、设备在线率、设备上报及时率、OCR 匹配成功率、库存周转率、库存准确率、采购到货及时率、订单毛利率、应付余额、维保费用。

规则：

- 口径以 V1.4 第 11 章为准，`bi_metric.definition` 写明分子/分母和过滤条件。
- **不可计算的指标一律不进入 KPI 卡片**，只在“指标口径”弹窗和看板的“待数据源”条里出现，并标注缺失原因。
- 绝不用 0、估算值或近似值冒充实测指标（例：工单只有计划米数，不能当成实际产量；设备状态日志不足以算利用率）。
- 成本与毛利（`order_gross_margin`、`ap_balance`）标记为 `PENDING_SOURCE`，并额外要求 `bi:cost:read` 权限，只对 ADMIN 与 FINANCE 可见。

#### 7.11.4 数据范围与权限

| 角色 | 范围 | 说明 |
| --- | --- | --- |
| ADMIN / FINANCE / 客户端凭证令牌 | 全公司 | 财务与机器令牌按组织口径 |
| FACTORY_MANAGER | 本厂 | 订单、应收、收款、审批按 `factory_id`；设备、库存、队列按工厂 |
| SALES / OPERATOR | 本人数据 | 订单按 `owner_user_id`，应收按业务员，报销按报销人，借款按借款人，审批按申请人 |

数据范围**在 SQL 层拼接**，不是查全量再在 Java 里裁剪；没有权限的模块直接不查、不返回，而不是返回全公司汇总让前端隐藏。

> 看板卡片按**底层业务模块的读取权限**逐个下发：没有 `order:read` 就没有订单卡片，没有 `finance:receipt:read` 就没有收款卡片。因此只申请 `bi.read` 的客户端凭证令牌能看到看板结构但没有任何汇总数字——这是最小权限的预期行为，不是故障。

| 权限码 | 用途 | 授予角色 |
| --- | --- | --- |
| `bi:dashboard:read` | 工作台/大屏数据与指标口径 | 全部员工角色 |
| `bi:cockpit:read` | 经营大屏页面 | ADMIN、FACTORY_MANAGER、FINANCE |
| `bi:cost:read` | 成本毛利类指标 | ADMIN、FINANCE |
| `bi:etl:run` | 手动触发 ETL | ADMIN |

`ops-client`（客户端凭证）通过 OAuth2 scope `bi.read` 获得 `bi:dashboard:read` + `bi:cockpit:read`，数据范围按组织处理；要看具体模块的卡片还需要同时申请对应的业务读取 scope。

#### 7.11.5 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/bi/dashboard` | `bi:dashboard:read` | 聚合看板：元数据、KPI 卡片、近 12 个月订单/收款趋势、工厂对比、订单/设备状态分布、风险预警、待数据源指标 |
| GET | `/api/bi/metrics` | `bi:dashboard:read` | 全部指标口径（按当前用户权限过滤） |
| POST | `/api/bi/admin/refresh` | `bi:etl:run` | 手动触发一次 ETL，返回是否执行、最近状态与最后成功时间 |

#### 7.11.6 前端

- 工作台（`/dashboard`）：顶部经营指标条 + KPI 卡片 + 近 12 个月订单/收款趋势 + 本月订单状态环图 + 风险与待办 + “待数据源”条；右侧“指标口径”弹窗展示 `bi_metric` 全量口径。
- 经营大屏（`/cockpit`）：独立路由，**16:9 暗色主题**，按 1920×1080 / 4K 视口等比缩放（`width: min(100vw, 100vh * 16/9)`），支持全屏、每 5 分钟自动刷新、刷新失败保留上一版快照并提示“数据刷新延迟”，底部滚动显示数据范围、最后同步时间与待数据源数量。
- 图表使用 ECharts 6，封装在 `components/ChartPanel.vue`（只负责实例生命周期与自适应，option 由页面生成）。
- 敏感数据（客户联系方式、合同正文、成本明细）不出现在任何 BI 响应中。

#### 7.11.7 已知限制

- NL2SQL / 自然语言取数**未实现**；`bi_metric` 指标语义层是它的前置基础，后续应在此之上做受控 NL2Metric，而不是让模型直接跑生产库 SQL。
- 订单完成率只按订单状态统计，发货与交付确认模块未实现；准时交付率因此标记为待数据源。
- 缺料预警按工厂 + 物料汇总后与物料安全库存比较，尚未实现按仓库的安全库存口径。
- 库存金额按“数量 × 最近一次**已入库**采购单价”截断到分；无采购入库记录的物料按 0 计，材料成本只认采购入库单。
- 调拨在途只统计 `PENDING`/`IN_TRANSIT` 两种状态。
- 队列平均等待只统计已经实际开工的队列（`RUNNING`/`DONE`），等待中的不进入均值。
- 生产应把 BI 拆成只读查询账号与 ETL 写入账号，并把 `company_app` 在 `company_bi` 上的权限收窄。

### 7.12 网关

| 路径前缀 | 目标 |
| --- | --- |
| `/api/system/**` | `lb://platform-system` |
| `/api/customer/**` | `lb://platform-customer` |
| `/api/finance/**` | `lb://platform-customer`（财务应收/收款/开票/对账暂由客户服务托管） |
| `/api/order/**` | `lb://platform-order` |
| `/api/approval/**` | `lb://platform-approval` |
| `/api/ai/**` | `lb://platform-ai`（只读 AI 助手；单路由响应超时 300 秒） |
| `/api/bi/**` | `lb://platform-bi`（只读 BI 聚合；不承接业务写入） |
| `/api/iot/**` | `lb://platform-order`（设备上报入口，设备签名鉴权，不走 OAuth 令牌） |
| `/actuator/health` | 网关自身 |

## 8. 安全与生产化注意事项

当前配置仅适用于内网开发：

- 默认密码、数据库口令、`ops-client` 密钥均为开发值，生产必须通过环境变量或配置中心替换。
- JWT 签名密钥库 `platform-auth/src/main/resources/auth-jwt.p12` 是开发密钥，生产必须更换并妥善保管；`changeit` 仅为开发口令。
- 网关和资源服务器的 `jwk-set-uri`、认证服务的 `issuer` 目前使用 `localhost`。部署到多机/容器后必须改为内网服务地址或域名，并保证 issuer 与前端配置一致。
- CORS 白名单目前包含本地开发地址和 `你的IP地址:5173`，生产需按实际前端域名配置并移除宽泛来源。
- 尚未启用 HTTPS、域名、ICP、WAF、限流、审计日志、JWT 吊销和单点登出。
- EMQX Dashboard 开发口令为 `root@123`，MySQL/Redis/IoTDB 使用 `root`；Nacos 鉴权配置见 4.4。生产必须启用认证/ACL/TLS 并修改全部默认口令。
- Nacos 服务端 API 鉴权当前为关闭状态，仅适用于内网开发；生产必须开启鉴权、创建业务账号、配置客户端用户名/密码，并限制 8848/9848/9849/8080 的访问来源。
- 设备接入参数（MQTT/CoAP、IoTDB、OCR 阈值、上报频率与保留周期）在 `iot.*` 配置中，初始化默认值见 `config/application-iot.yml`；生产必须修改演示设备密钥、启用 Broker 认证/TLS，并通过环境变量注入 IoTDB 口令。
- 审批中心已落地信用额度调整、队列插队、费用报销、借款四个业务动作；发票作废/红冲、仓储单据的审批需要按同一模式接入 `ApprovalActionHandler`。
- AI 助手只读，没有审批/付款/改数据工具；但模型输出仍可能受提示词注入影响，生产必须保持工具白名单、JWT 透传、只读接口和审计，不得把模型输出直接当作业务指令。
- Ollama `11434` 默认无认证，当前仅适合内网。生产必须限制来源（只允许 `platform-ai` 所在主机）、关闭公网访问，并按需增加反向代理认证/限流；不要把 Ollama 端口暴露到办公网之外。

## 9. 已知问题与排障

### 9.1 端口被旧进程占用

`start-all.ps1` 只管理自己 PID 文件对应的进程。若之前手动启动过服务，可能出现“健康检查通过但代码是旧版本”的情况。排查：

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Select-Object ProcessId, CommandLine

Get-NetTCPConnection -State Listen |
  Where-Object { $_.LocalPort -in 9000,9101,9102,9103,8080 } |
  Select-Object LocalPort, OwningProcess
```

确认是本项目进程后可强制停止，再执行 `start-all.ps1 -Restart`。

### 9.2 ShardingSphere 启动失败

`platform-order` 必须显式包含以下运行时依赖，不能只依赖 `shardingsphere-jdbc`：

- `shardingsphere-infra-data-source-pool-hikari`：把 Hikari 的 `jdbcUrl` 映射为 ShardingSphere 需要的 `url`；
- `shardingsphere-parser-sql-engine-mysql`：MySQL 方言；
- `shardingsphere-database-connector-mysql`：数据库元数据；
- `shardingsphere-standalone-mode-core` / `shardingsphere-standalone-mode-repository-memory`：单机模式上下文与内存元数据仓库；
- `shardingsphere-authority-simple`：权限 SPI；
- `commons-lang3` 3.18.0：Spring Boot 3.5 默认管理的 3.17.0 缺少 ShardingSphere 使用的 `org.apache.commons.lang3.Strings`。

### 9.3 登录页返回 401

认证服务的默认安全链不能同时设置全局 `authenticationEntryPoint` 和依赖默认登录页。当前实现使用按媒体类型注册的 `defaultAuthenticationEntryPointFor`：

- `Accept: text/html` → 跳转 `/login`；
- 非 HTML → JSON 401。

如果后续修改 `SecurityConfig`，请保留这个区分，否则 `/oauth2/authorize` 会在登录前直接返回 401。

### 9.4 `commons-lang3` 版本

不要在 `platform-order` 中恢复为 Spring Boot 管理的默认版本；否则启动时可能出现：

```text
NoClassDefFoundError: org/apache/commons/lang3/Strings
```

### 9.5 认证服务升级后旧授权记录

当前实现把标准 `org.springframework.security.core.userdetails.User` 存入 JDBC 授权记录，令牌签发时再按用户名加载平台用户详情。如果从更早的构建升级（早期版本直接序列化 `PlatformUserPrincipal`），旧的 `oauth2_authorization` 行可能无法反序列化。开发环境可清空授权表后重新登录：

```sql
DELETE FROM oauth2_authorization_consent;
DELETE FROM oauth2_authorization;
```

生产环境不要直接清空；应通过灰度/重新登录或一次性迁移脚本处理。

### 9.6 Nacos 注册失败或网关 503

先检查 Nacos 健康状态和服务列表：

```powershell
Invoke-RestMethod http://你的IP地址:8848/nacos/actuator/health
Invoke-RestMethod "http://你的IP地址:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=20&namespaceId=public"
```

如果服务列表为空或实例 IP 不正确：

1. 确认各服务日志中出现 `nacos registry, DEFAULT_GROUP ... register finished`；
2. 确认 `NACOS_CLIENT_IP` 是当前开发机的内网地址（默认 `你的IP地址`），不是虚拟网卡或回环地址；
3. 确认网关能访问 Nacos 返回的实例 IP/端口；
4. 确认 Nacos 服务端没有开启鉴权，或客户端已正确配置 `username/password`。

网关启动后首次调用仍可能出现短暂 503；`start-all.ps1` 会等待六个业务服务都出现在 Nacos 服务列表后再结束。

### 9.7 订单创建提示客户服务不可用或合同校验失败

订单创建会在服务端调用 `platform-customer`：

1. 确认 `platform-customer` 已启动并注册到 Nacos（端口 `9103`）；
2. 确认数据库已执行 `sql/07-customer-contract-schema.sql`，客户/合同演示数据存在；
3. 确认 `company_order0/1` 的 8 张物理表已补 `contract_id` 列（重新执行 `sql/03`、`sql/04` 即可幂等迁移）；
4. 409 通常是业务阻断（客户非合作中、合同未录入/过期/额度不足、信用或逾期超限），按返回文案处理，不要绕过服务端校验。

### 9.8 AI 助手超时、503 或工具调用失败

按以下顺序排查：

1. `platform-ai` 是否已启动并注册 Nacos：`http://localhost:9106/actuator/health`，Nacos 服务列表应包含 `platform-ai`。
2. Ollama 是否可达：`curl http://你的IP地址:11434/api/tags`；Windows 开发机也要能访问该地址。
3. 模型是否已拉取：`ollama list` 中必须有 `qwen3:4b-instruct-2507-q4_K_M`；首次加载查看 `journalctl -u ollama -n 100 --no-pager`。
4. 网关是否已重启并加载 `/api/ai/**` 路由；旧网关进程不会自动读取新配置。
5. 用户令牌是否包含 `ai:assistant:use`；权限是签发时写入 JWT 的，执行 `sql/17-ai-assistant.sql` 后必须重新登录。
6. 工具返回 403 表示当前用户缺少对应业务权限（如销售查他人借款、非审批人查待办），这是预期的数据范围控制，不是 AI 故障。
7. CPU 推理较慢，单请求 20–120 秒属正常；AI 内部总超时 240 秒，超过会返回 504，网关/前端上限 300 秒。若频繁超时，检查服务器内存是否被其他进程占用，并确认 `OLLAMA_MAX_LOADED_MODELS=1`、`OLLAMA_NUM_PARALLEL=1`。

### 9.9 BI 看板无数据或数据过期

按以下顺序排查：

1. `company_bi` 是否已创建：执行 `sql/18-bi-dashboard.sql`（需要能建库的账号，`company_app` 无权 `CREATE DATABASE`），并确认 `company_app` 已被授权访问 `company_bi`。
2. `platform-bi` 是否已启动并注册 Nacos：`http://localhost:9105/actuator/health`，Nacos 服务列表应包含 `platform-bi`；网关需要重启才能加载 `/api/bi/**` 路由。
3. ETL 是否成功：查 `company_bi.bi_etl_run` 最新一行，`status` 必须是 `SUCCESS`；失败时 `message` 里有原因，服务日志关键字为 `BI ETL failed`。
4. 看板显示“数据刷新延迟”通常表示**最后成功时间超过 15 分钟**（`refreshMs` 的两倍）。先确认 ETL 没有持续失败，再看 `bi_etl_run.finished_at`。
5. “最后同步时间”偏差 8 小时：数据库 `NOW()` 走数据库服务器时区（UTC），平台业务表用的是应用本地时钟。ETL 运行时间已改为由应用写入，不要在 SQL 里混用 `NOW()`。
6. 指标全部为 0 或缺失：先确认业务库里确实有数据且属于当前用户的数据范围（销售只看本人、厂长只看本厂），再看 `bi_metric` 中该指标是否为 `PENDING_SOURCE`——待数据源的指标本来就不会出现在卡片里。
7. 手动触发一次 ETL 验证链路：ADMIN 令牌调用 `POST /api/bi/admin/refresh`（需要 `bi:etl:run`）。
8. 权限变更后必须重新登录：权限是签发时写入 JWT 的，执行 `sql/18-bi-dashboard.sql` 追加权限后旧令牌不会自动生效。

## 10. 后续实现建议

按 V1.4 的业务依赖顺序推进：

1. **客户与合同（基础版已完成）**：客户档案、销售归属、合同录入、订单提交前合同与信用校验已接入；后续补客户交接/合并、图案库、报价单和审批变更单。
2. **财务规则**：应收、收款核销、手工开票登记、客户对账单、费用报销、借款/还款已完成；后续补发票作废/红冲审批、逾期 30 天发货拦截、应付与付款、审批金额档位配置。
3. **设备与订单排产（基础版已完成）**：设备档案、单设备单工单、厂内拆单、FIFO 队列、插队申请与厂长审批、被插队销售站内通知、维护工单与点检保养记录已实现；后续补点检计划排程、维护工单审批和设备移机审批。
4. **仓储（基础版已完成）**：每厂主仓、物料档案、库存台账、采购入库（材料成本唯一来源）、跨厂调拨、工单领料与工单材料成本已实现；后续补工单成本结转到订单、仓储单据审批和库存盘点。
5. **流程审批与消息**：审批中心已独立成服务（审批定义/金额档位、实例、待办、轨迹），已接入信用额度调整、队列插队、费用报销与借款；后续接入发票作废/红冲、仓储单据审批。
6. **设备上报（基础版已完成）**：MQTT 3.1.1 订阅 + CoAP + HTTP 三条接入通道，订单作业数据入 MySQL 并覆盖更新、自检/故障/心跳入 IoTDB、订单图片一条一单、OCR 订单号精确匹配与未匹配暂存；后续补 TLS/ACL、灰度发布与设备固件版本管理。
7. **PC Web、首页大屏与 AI**：客户、合同、订单、设备台账、排产队列、插队审批、费用报销、借款与还款、消息中心、AI 助手页面与权限控制已接入；首页工作台与经营大屏的聚合接口、BI 指标语义层（`bi_metric`）已由 `platform-bi` 落地，下一步在指标语义层之上做受控 NL2Metric/NL2SQL（只读视图 + 指标口径），不在业务库上直接跑模型 SQL。
8. **生产化**：域名/ICP/HTTPS、密钥与口令轮换、EMQX ACL/TLS、Ollama 端口收敛、备份恢复、可观测性与审计。

新增业务模块时，建议沿用当前结构：`web`（Controller/Command/View）→ `service` → `mapper` → `entity`，公共响应、异常、JWT、CORS 和 TraceId 继续复用 `platform-common`。

## 11. 相关文档

- `docs/公司管理系统-模块功能规划-v1.4.md`：有效业务规则
- `docs/开发任务清单-EpicStory-v1.0.md`：功能拆解
- `docs/设备接入配置模板与报文示例-v1.0.md`：设备上报配置与报文
- `docs/页面原型清单与权限矩阵-v1.0.md`：页面与权限
- `company-platform-frontend` 仓库（`company-platform-web/README.md`）：PC Web 启动、环境变量、OAuth2 回调和部署说明

## 12. 页面截图

> PC Web 每个页面与路由、截图文件的完整对照表见前端仓库 `company-platform-frontend` 的「10. 页面截图」章节；截图文件统一存放于前端仓库的 `docs/screenshots/`，本后端仓库不提交截图二进制文件。下表是后端交付面最常用的几张：

| 交付面 | 截图文件 | 说明 |
| --- | --- | --- |
| 统一认证 | `docs/screenshots/web-login.png` | OAuth2 授权码 + PKCE 登录入口与认证中心跳转 |
| 经营指标（工作台） | `docs/screenshots/web-dashboard.png` | KPI 卡片、近 12 个月订单/收款趋势、本月订单状态环图、风险与待办、「待数据源」条、指标口径弹窗 |
| 经营大屏 | `docs/screenshots/web-cockpit.png` | 16:9 暗色驾驶舱：6 个 KPI 卡、订单与收款趋势、设备状态环图、工厂订单对比、风险预警；支持全屏与 5 分钟自动刷新 |
| 财务与信用 | `docs/screenshots/web-credit.png` | 授信额度占用、可用额度、最大逾期与风险标签 |
| 设备与排产 | `docs/screenshots/web-schedule.png` | 设备台账、生产中工单与 FIFO 等待队列 |
| 仓储 | `docs/screenshots/web-warehouse.png` | 仓库档案、物料档案、库存台账与采购入库 |
| 审批中心 | `docs/screenshots/web-approval.png` | 我的待办 / 我的申请、审批轨迹与金额档位 |
| AI 助手 | `docs/screenshots/web-assistant.png` | 只读问答与工具调用过程（审计写入 `ai_chat_log`） |

后端本身没有独立 Web 界面，可对外展示的是 Nacos 控制台（`http://你的IP地址:8080/`）与各服务健康检查端点（`/actuator/health`）。
