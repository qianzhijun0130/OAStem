package com.kellen.oa_system.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动期 Redis 连通性检查。
 *
 * <p>为什么单独做一个检查器：Redis 是本次新引入的**外部依赖**，如果它没起来，
 * 最坏的表现是「登录变慢 / 黑名单不生效」这类静默降级——排查时很难第一时间想到是 Redis。
 * 启动日志里明确写一条，运维一眼可见。</p>
 *
 * <p>默认不阻断启动（{@code redis.fail-fast=false}）：Redis 在本地开发机上未必常驻，
 * 强制它成为启动前提会让「只想跑后端看接口」变得麻烦。若部署环境把 Redis 当硬依赖，
 * 把该开关置 true 即可让启动直接失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStartupChecker implements ApplicationRunner {

    private final RedisGuard redisGuard;

    @Value("${redis.fail-fast:false}")
    private boolean failFast;

    @Override
    public void run(ApplicationArguments args) {
        String result = redisGuard.ping();
        if (result != null) {
            log.info("[Redis 检查] 连接正常：{}", result);
            return;
        }
        if (failFast) {
            throw new IllegalStateException("[Redis 检查] 连接失败，且 redis.fail-fast=true，启动中止");
        }
        log.warn("[Redis 检查] 连接失败，以下能力将降级："
                + "① 登出令牌无法立即失效（仍受令牌有效期约束）"
                + "② 字典缓存关闭，每次查库"
                + "③ 登录失败限流关闭");
    }
}
