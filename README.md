# OASystem · 办公自动化系统

基于 Spring Boot 4 + Vue 3 的企业级 OA 系统，覆盖组织与权限、审批流程、低代码表单与数据看板。

> 个人全栈项目，重点在**企业级后端的工程实践**：RBAC 与数据权限、Flowable 流程引擎集成、低代码表单与节点字段权限、缓存与故障降级。

---

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 4.1.1 · Spring Security · MyBatis-Plus · Flowable 8.0.0 |
| 前端 | Vue 3.5 · TypeScript · Vite 8 · Element Plus 2.14 · Pinia · Vue Router 4 |
| 数据库 | MySQL 8.0（utf8mb4 / utf8mb4_unicode_ci） |
| 缓存 | Redis（登出黑名单、字典缓存、登录限流；**可选**，未启动时自动降级） |
| 流程与表单 | bpmn-js 18 · bpmn-js-properties-panel · FormCreate 3 |
| 其他 | JWT（jjwt）· Apache POI（Excel 导入导出）· Lucide 图标 · ECharts |

---

## 功能模块

顶级菜单共 8 个：

| 模块 | 能力 |
| --- | --- |
| **工作台** | 问候条、应用中心、我的待办 / 已办 / 我发起的 三态列表、通知公告、日程日历（红点=当日待办数） |
| **发起流程** | 按分类浏览已启用流程模板，点卡片进入填单页；支持「提交」直接发起、「保存」存草稿 |
| **任务中心** | 我的流程（草稿可再次提交）、待办任务、已办任务、抄送任务（未读/全部、标记已读） |
| **流程管理** | 流程模板（BPMN 设计器 + 审批人策略 + 条件分支 + 流程图标）、表单设计（低代码）、实例监控（终止/挂起/删除） |
| **分析中心** | 待办分析：4 项 KPI、模块维度指标卡、异常轨迹 |
| **公告中心** | 公告管理（可按部门/人员定向可见）、我的公告 |
| **日志管理** | 登录日志、操作日志（按数据权限分级可见，支持导出） |
| **系统管理** | 用户、角色、菜单、部门、系统字典 |

**权限模型**：RBAC（用户 → 角色 → 菜单/按钮权限码）+ 数据权限（全部 / 本部门及下级 / 仅本人）。接口鉴权读 JWT 内的权限码，因此改权限后需重新登录生效；侧边栏菜单实时查库。

**审批能力**：6 类审批人策略（指定人员 / 角色 / 申请人部门主管 / 申请人本人 / 指定部门主管 / 未配置）、节点表单绑定、节点字段权限（可见·可编辑·必填）、条件网关、超时提醒与自动驳回、挂起与恢复。

---

## 项目结构

```
OAStem/
├─ oa-system/                        # 后端（Spring Boot）
│  ├─ src/main/java/com/kellen/oa_system/
│  │  ├─ common/                     # Result、错误码、全局异常、Redis 封装
│  │  ├─ config/                     # Security、MyBatis-Plus、WebMvc
│  │  ├─ security/                   # JWT、令牌黑名单、登录限流
│  │  ├─ system/                     # 用户/角色/菜单/部门/字典/公告/日志/表单
│  │  └─ workflow/                   # 流程定义、审批、抄送、节点权限、条件分支
│  ├─ src/main/resources/            # 配置、BPMN 种子流程
│  └─ 数据库/                         # 模块级建表与菜单补丁脚本
├─ vue3-admin-template/              # 前端（Vue 3 + Vite）
│  └─ src/{api,components,composables,layout,router,stores,utils,views}
├─ 开发文档/                          # 设计文档、操作日志、数据库脚本
└─ 测试文档/                          # 分模块测试用例与执行记录
```

---

## 快速开始

### 1. 环境要求

- JDK 17
- MySQL 8.0
- Node.js 20+
- Redis 3.0+（可选，不启动也能运行）

### 2. 初始化数据库

```bash
# 建库
mysql -uroot -p -e "CREATE DATABASE oa_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

# 基础表结构与初始数据
mysql -uroot -p oa_system < 开发文档/数据库/oa_system_init.sql
mysql -uroot -p oa_system < 开发文档/数据库/oa_system_init_data.sql

# 模块补丁（流程、表单、节点表单与字段权限、条件分支、字典、抄送）
cd oa-system/数据库
for f in flow_tables.sql form_tables.sql node_field_permission_tables.sql \
         flow_condition_tables.sql dict_tables.sql approval_cc_table.sql; do
  mysql -uroot -p oa_system < "$f"
done
```

> Flowable 的 `ACT_*` / `FLW_*` 表由引擎首次启动自动创建（`flowable.database-schema-update=true`），无需手工建。
> `*_menu.sql` 是各阶段菜单补丁，按需执行；完整菜单数据已包含在 `oa_system_init_data.sql` 中。

### 3. 配置后端

```bash
cd oa-system/src/main/resources
cp application-local.properties.example application-local.properties
# 编辑该文件：数据库密码、JWT 密钥（该文件已被 .gitignore 忽略，不会入库）
```

### 4. 启动后端

```bash
cd oa-system
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

启动后监听 `http://localhost:8080`。日志中会出现三项自检结果：权限码校验、菜单完整性校验、Redis 连通性。

### 5. 启动前端

```bash
cd vue3-admin-template
npm install
npm run dev
```

浏览器打开 `http://localhost:3000`（Vite 通过 `/api` 前缀代理到 8080，无需额外配跨域）。

### 6. 默认账号

| 账号 | 密码 | 角色 | 数据范围 |
| --- | --- | --- | --- |
| `admin` | `Admin@123456` | 超级管理员 | 全部 |
| `tech_admin` | `qwe@123456` | 技术经理 | 本部门及下级 |
| `tech_staff1` / `tech_staff2` | `qwe@123456` | 技术专员 | 仅本人 |
| `hr_admin` / `hr_staff1` / `hr_staff2` | `qwe@123456` | 人事经理 / 人事专员 | 经理=本部门，专员=本人 |
| `fin_admin` / `fin_staff1` / `fin_staff2` | `qwe@123456` | 财务经理 / 财务专员 | 同上 |
| `mkt_admin` / `mkt_staff1` / `mkt_staff2` | `qwe@123456` | 市场经理 / 市场专员 | 同上 |

除 `admin` 外，其余 12 个内置账号密码统一为 `qwe@123456`。

---

## 配置说明

配置分两层：`application.properties`（环境无关）与 `application-local.properties`（敏感项，已忽略）。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 后端端口 |
| `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/oa_system` | 库名与连接排序规则需为 `utf8mb4_unicode_ci` |
| `jwt.expiration` | `28800000` | 令牌有效期（毫秒，默认 8 小时） |
| `spring.data.redis.*` | `127.0.0.1:6379` db0 | Redis 连接；**不要写空的 password**（Lettuce 空串仍会发 AUTH，导致连接失败） |
| `redis.fail-fast` | `false` | `true` 时 Redis 连不上则启动失败 |
| `redis.cache.dict-ttl-seconds` | `600` | 字典缓存有效期 |
| `security.login.max-failures` | `5` | 登录失败阈值（窗口 300s，锁定 300s） |
| `approval.timeout.hours` | `48` | 节点默认超时小时数（0 = 关闭） |
| `flowable.database-schema-update` | `true` | 首次启动自动建引擎表 |

**Redis 是可选的**：未启动时字典缓存回源数据库、登出黑名单放行、登录限流关闭，业务不受影响；启动日志会给出明确 WARN。

---

## 设计要点

1. **逻辑删除与唯一索引共存**
   唯一索引形如 `(业务编码, active_flag)`，`active_flag` 是生成列 `IF(is_deleted=0, 1, NULL)`。借助「唯一索引允许多个 NULL」，已删行自动让位，业务值永不被改写，删掉后同一编码可立即复用。

2. **给无状态 JWT 补上撤销能力**
   登出时把令牌的 SHA-256 摘要写入 Redis 黑名单（TTL = 令牌剩余寿命），鉴权过滤器解析后先查黑名单。只存摘要不存明文，Redis 泄露也拿不到可用令牌。

3. **缓存一律可降级**
   `RedisGuard` 是唯一访问入口：异常时缓存回源、限流关闭、黑名单放行；失败后 30 秒熔断冷却，避免每个请求都付出 3 秒连接超时，冷却结束自动半开重试。

4. **审批人配置读库不读 BPMN**
   设计器只把审批人策略写入节点配置（`node_config`），运行期按配置解析候选人；BPMN 只描述流程结构，部署时校验结构完整性（起止事件、网关分支、孤立节点）。

5. **草稿与提交分离**
   `POST /draft` 只落单据不起引擎，`PUT /draft/{id}` 可反复保存，`POST /{id}/submit` 才启动流程。单据编号 `{流程编码}-{yyyyMMdd}-{四位序号}` 在保存草稿时生成并全程不变。

6. **前端收敛重复**
   列表页统一用 `ListPage` + `useListQuery`；图标统一走 Lucide（`AppIcon` 注册表）；`ElButton` 全局包装，按文案自动配图标并带 `title` 悬停提示，新增页面无需逐个处理。

---

## 数据库

- 业务表 **40** 张（`oa_*`）
- Flowable 引擎表 **36** 张（`ACT_*` / `FLW_*`，首次启动自动创建）

表结构以 `开发文档/数据库/oa_system_init.sql` 为主，后续模块变更见 `oa-system/数据库/*.sql` 与 `开发文档/操作日志/数据库操作日志.md`。

---

## 文档

| 文档 | 内容 |
| --- | --- |
| `开发文档/权限体系详解.md` | RBAC、数据权限、权限码校验机制 |
| `开发文档/Redis缓存与登出黑名单.md` | 缓存点、key 规范、降级策略、扩展方式 |
| `开发文档/前端/组件-ListPage与useListQuery.md` | 列表页骨架与分页约定 |
| `开发文档/数据库/` | 各业务模块表设计说明 |
| `开发文档/操作日志/` | 后端 / 前端 / 数据库 / 项目评估 四份变更日志 |
| `测试文档/` | M01~M13 分模块测试用例与执行记录 |

---

## 已知限制

- 单机部署，未做集群、分布式会话与读写分离。
- Redis 为单实例，无主从/哨兵；不可用时按上述策略降级。
- 抄送仅做告知，不产生待办、不影响流程流转。
- 管理员无法强制下线在线用户（只能停用账号或等待令牌过期）。
- 前端只有开发与构建脚本，未引入单元测试；后端以接口级脚本与浏览器自动化验证为主。
- 项目定位为学习与展示，未经过生产流量验证，部署到公网前需自行加固（HTTPS、密钥轮换、访问审计等）。
