package com.kellen.oa_system.security.crypto;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 敏感字段加密组件的启动装配。
 *
 * <p>MyBatis 的 TypeHandler 由框架反射创建、拿不到 Spring 容器，
 * 因此这里在启动时把 Spring 管理的 {@link AesCrypto} 装配进静态入口。
 * 构造 {@link AesCrypto} 时会做一次加解密自检，密钥缺失或错误都会让应用<b>启动失败</b>——
 * 这是有意为之：宁可起不来，也不要带着明文数据静默运行。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoBootstrap {

    private final CryptoProperties properties;

    @PostConstruct
    public void init() {
        AesCrypto.install(new AesCrypto(properties.getKey()));
        log.info("敏感字段加密已启用（AES-256-GCM），启动期存量迁移={}", properties.isMigrateOnStartup());
    }
}
