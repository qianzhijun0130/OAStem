# Redis 主从 + Sentinel 高可用（演练/部署说明）

本目录提供一套**可在本机直接跑起来**的 Redis 主从复制 + 三哨兵配置，用于验证应用侧的
高可用接入（`spring.profiles.active=redis-ha`）以及故障切换后的行为。

## 1. 拓扑

| 角色 | 端口 | 配置文件 | 说明 |
| --- | --- | --- | --- |
| master | 6381 | `master.conf.template` | 唯一可写节点 |
| replica | 6382 | `replica.conf.template` | `replicaof 127.0.0.1 6381`，只读 |
| sentinel ×3 | 26381 / 26382 / 26383 | `sentinel.conf.template` | `quorum 2`、`down-after 3000ms` |

> 端口刻意**不用 6379**：开发机上 6379 常被其它项目共用，演练不应影响别人的数据。

**为什么是三个哨兵**：故障转移需要「多数派」选主。只起两个时，挂掉一个就凑不出多数票，
切换会卡住——这是本地演练最常见的坑。三个哨兵 + `quorum 2` 才是能真正切过去的最小配置。

## 2. 启动 / 停止

```powershell
# 启动（默认从 D:\Program Files\Redis 找 redis-server.exe，可用 REDIS_HOME 或 -RedisHome 覆盖）
./start-ha.ps1

# 核对状态
D:\Program Files\Redis\redis-cli.exe -p 6381 INFO replication
D:\Program Files\Redis\redis-cli.exe -p 26381 SENTINEL MASTER oastem-master
D:\Program Files\Redis\redis-cli.exe -p 26381 SENTINEL SENTINELS oastem-master

# 停止（只停 6381/6382/2638x，不碰 6379）
./stop-ha.ps1
./stop-ha.ps1 -PurgeData     # 连带清掉 runtime/ 里的数据与日志
```

运行期配置、RDB、日志都写在 `runtime/`（已 gitignore）：Sentinel 在故障转移时会**重写自己的
配置文件**（记录新 master 与发现的从节点），因此不能拿模板文件直接跑。

## 3. 应用侧接入

哨兵模式收敛在独立的 profile 文件里，**默认仍是单机**，不改变开发体验：

```bash
# 单机（默认）
./mvnw spring-boot:run

# 哨兵
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=redis-ha"
```

`application-redis-ha.properties` 内容：

```properties
spring.data.redis.sentinel.master=oastem-master
spring.data.redis.sentinel.nodes=127.0.0.1:26381,127.0.0.1:26382,127.0.0.1:26383
```

启动日志会打印拓扑与角色，可直接确认连到了谁：

```
[Redis 检查] 拓扑：哨兵模式（master=oastem-master，sentinels=127.0.0.1:26381,...）
[Redis 检查] 连接正常：PONG | redis 3.0.504 | role master
```

## 4. 故障切换演练

```powershell
# 1) 记下当前 master
& $redisCli -p 26381 SENTINEL GET-MASTER-ADDR-BY-NAME oastem-master   # -> 127.0.0.1 6381

# 2) 杀掉 master 进程
Get-NetTCPConnection -LocalPort 6381 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }

# 3) 等 5~10 秒，哨兵应把 6382 提升为新 master
& $redisCli -p 26381 SENTINEL GET-MASTER-ADDR-BY-NAME oastem-master   # -> 127.0.0.1 6382
```

应用侧预期行为：

- Lettuce 通过哨兵重新解析 master 地址，**无需重启应用**；
- 切换窗口内（约 3~10 秒）Redis 调用失败，`RedisGuard` 按既有约定**降级不阻断**：
  登出黑名单放行（令牌仍受有效期约束）、字典回源数据库、登录限流关闭；
- 熔断冷却默认 30 秒，切换完成后自动半开重试并恢复，日志里会出现一次恢复记录。

> 换言之：**故障切换期间业务可用性优先于缓存一致性**。这是本项目的既定策略，
> 不是本次新增的行为；HA 只是把「Redis 彻底挂了」的窗口从「人工介入前一直降级」缩短到「切换的几秒」。

## 5. 生产部署要点（与本机演练的差异）

| 项 | 本机演练 | 生产建议 |
| --- | --- | --- |
| 节点分布 | 同机不同端口 | master / replica / 哨兵分机器，避免整机掉电全丢 |
| 持久化 | 关闭 RDB/AOF | 主从都开 AOF `everysec`，切换后数据不丢 |
| 认证 | 无密码 | `requirepass` + `masterauth`，哨兵侧配 `sentinel auth-pass` |
| 判定时长 | `down-after 3000` | 10~30s，避免网络抖动触发误切换 |
| 哨兵数量 | 3（同机） | ≥3 且分布在 ≥2 台机器 |

## 6. 与代码的关系

应用侧的 Redis 访问**全部经 `RedisGuard`**（`common/redis/RedisGuard.java`），
连接层换成哨兵对业务代码零侵入：`StringRedisTemplate` 由 Spring Boot 按 profile 装配，
`RedisGuard` 只负责失败降级与熔断。本次仅补了两处可观测性：

1. `RedisStartupChecker` 打印当前拓扑（单机 / 哨兵 master 名与地址）；
2. `RedisGuard#ping` 追加 `role master|slave`，便于确认切换后连的是新主还是旧从。
