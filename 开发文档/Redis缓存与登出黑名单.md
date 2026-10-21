# Redis 缓存与登出黑名单

> 接入时间：2026-10-22｜涉及：`oa-system` 后端，前端无改动
> 本地实例：`D:/Program Files/Redis`（3.0.504，端口 6379，db 0，无密码）

---

## 一、为什么接 Redis

原系统是「MySQL + 无状态 JWT」的结构，Redis 不是非加不可，但补上了三个真实缺口：

| 缺口 | 原状态 | 接入后 |
| --- | --- | --- |
| 登出不可撤销 | JWT 无状态，登出只清前端本地存储；令牌在 8 小时有效期内被截获仍可重放 | 登出即写入黑名单，同一令牌立刻 401 |
| 字典重复查库 | 前端每个下拉框每次打开都调 `/system/dict/data/type/{type}`，纯查库 | 命中缓存直接返回，TTL 600s，写操作主动失效 |
| 登录可被暴力破解 | 无失败次数限制 | 同一账号 5 次失败锁定 5 分钟 |

**没有做的事**（避免过度设计）：不缓存菜单/权限（权限变更语义要求实时，且改权限本就要求重登）、不做分布式锁、不做会话共享（仍是 JWT 无状态）、不引 Spring Cache 抽象（本地仓库无 `spring-boot-starter-cache`，且 `@Cacheable` 自调用失效是常见坑，这里用显式读写更可控）。

---

## 二、代码结构

```
common/redis/
  RedisGuard.java            # 唯一访问入口：异常降级 + 30s 熔断冷却
  RedisStartupChecker.java   # 启动 ping 与版本日志
security/
  TokenBlacklistService.java # 登出黑名单
  LoginFailureGuard.java     # 登录失败限流
```

业务接入点：

| 位置 | 改动 |
| --- | --- |
| `AuthController#logout` | 解析令牌 → 按剩余寿命写黑名单 |
| `JwtAuthenticationFilter` | 解析令牌后先查黑名单，命中则不建立认证上下文 |
| `DictDataServiceImpl#listByType` | 读缓存 → 未命中回源 → 回写 |
| `DictDataServiceImpl` 写方法 | 保存/删除/改状态后删对应类型缓存 |
| `DictTypeServiceImpl` 写方法 | 类型增删改状态后删对应类型缓存（新建也要删：类型不存在时「空列表」会被缓存） |
| `AuthServiceImpl#login` | 认证前查锁定、失败计数、成功清零 |

---

## 三、key 规范（重要）

本机 Redis **已被其他项目共用**（存在 `login:token:*` 等键），所以本项目所有键必须带 `oastem:` 前缀。

| key | TTL | 说明 |
| --- | --- | --- |
| `oastem:auth:blacklist:{sha256前32位}` | 令牌剩余寿命 | 只存摘要，不存明文令牌 |
| `oastem:dict:items:{dictType}` | 600s | 值是可读 JSON |
| `oastem:auth:login:fail:{username}` | 300s | 自增计数 |
| `oastem:auth:login:lock:{username}` | 300s | 锁定标记 |

排查命令：

```bash
redis-cli keys "oastem:*"                      # 看本项目的键
redis-cli ttl oastem:dict:items:flow_category  # 看剩余寿命（-2=不存在）
redis-cli del oastem:auth:login:lock:admin     # 手工解锁（账号被锁定时用）
```

⚠️ 两个版本限制（Redis 3.0）：
- 不支持 `UNLINK`，代码统一用 `DEL`；
- Windows 版 `redis-cli --scan --pattern` 会返回空，**只能用 `keys`**。

---

## 四、降级策略（Redis 挂了会怎样）

`RedisGuard` 的约定是**任何 Redis 故障都不让业务失败**：

| 能力 | Redis 不可用时 |
| --- | --- |
| 字典缓存 | 关闭，每次回源数据库（结果正确，只是慢） |
| 登出黑名单 | 放行 —— 旧令牌仍可用的窗口回到「令牌有效期」 |
| 登录限流 | 关闭 —— 不计数、不锁定（避免把所有人锁在门外） |
| 启动 | 默认**不阻断**，只打 WARN；`redis.fail-fast=true` 时中止启动 |

另有一层**熔断冷却**：连接超时 3s，若每个请求都去撞一次，挂掉的 Redis 会让全站请求各慢 3s。所以失败后 30s 内直接降级、不再尝试；冷却结束自动半开重试，Redis 恢复后**无需重启应用**。

验证方式（不动生产实例）：

```bash
./mvnw -o spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081 --spring.data.redis.port=6399"
```

实测结果：登录成功、字典查库正常、登出接口不报错、连错 6 次不误锁，日志输出
`Redis 不可用，接下来 30s 内相关功能降级到数据库（不影响主流程）`。

---

## 五、配置项

```properties
spring.data.redis.host=${REDIS_HOST:127.0.0.1}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.database=${REDIS_DATABASE:0}
spring.data.redis.timeout=3s
spring.data.redis.connect-timeout=3s
spring.data.redis.lettuce.shutdown-timeout=200ms

redis.fail-fast=false                 # true=连不上就启动失败（Redis 为硬依赖时用）
redis.cache.dict-ttl-seconds=600
security.login.max-failures=5
security.login.failure-window-seconds=300
security.login.lock-seconds=300
```

⚠️ **不要写 `spring.data.redis.password=`（空值）**：Lettuce 对空串仍会发送 `AUTH`，而 Redis 未设 `requirepass` 时会直接报
`ERR Client sent AUTH, but no password is set` 导致连接失败。需要密码时用 `REDIS_PASSWORD` 环境变量注入。

---

## 六、如何新增一个缓存点

1. 定 key：`oastem:<域>:<业务>:<标识>`，必须带 `oastem:` 前缀；
2. 读：`redisGuard.read(key, json -> objectMapper.readValue(json, new TypeReference<X>() {}))`，返回 `null` 即回源；
3. 回写：`redisGuard.write(key, objectMapper.writeValueAsString(v), Duration.ofSeconds(ttl))`；
4. **所有写路径都要失效**（新增/修改/删除/改状态/父级变更），漏一个就会出现「改了不生效」；
5. TTL 必须有：无 TTL 的缓存一旦失效逻辑漏了就是永久脏数据。

---

## 七、已知限制

- 黑名单只对**已登出**的令牌生效；管理员想立刻踢人，目前只能停用账号（`UserServiceImpl` 走 DB 校验的接口会拒绝），或等令牌过期。
- 单机 Redis，无主从/哨兵；换集群需把 `RedisGuard` 的 `StringRedisTemplate` 换成 `RedisClusterTemplate` 并调整 SCAN 逻辑。
- 字典缓存按 `dictType` 粒度失效，跨类型变更（如改类型编码——业务上已禁止）需要清前缀 `oastem:dict:items:`。
