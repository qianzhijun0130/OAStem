package com.kellen.oa_system.security.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 敏感字段加密配置。
 *
 * <p>设计原则：密钥不入库、不硬编码。本地开发写在 {@code application-local.properties}（已 gitignore），
 * 生产用环境变量 {@code CRYPTO_KEY} 注入。密钥可以是任意长度的随机串，
 * 内部以 SHA-256 派生为 256 位 AES 密钥，因此换密钥只会导致旧密文不可解，不会因长度不符而启动失败。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.crypto")
public class CryptoProperties {

    /**
     * 加密密钥（必填）。为空时启动期直接失败，避免"以为加密了其实是明文"的静默降级。
     */
    private String key;

    /**
     * 启动期是否把存量明文加密为密文。
     *
     * <p>迁移是幂等的：已是 {@code ENC:} 前缀的值直接跳过，因此重复启动不会二次加密。
     * 数据量大时可临时置 false，改用运维脚本按批处理。</p>
     */
    private boolean migrateOnStartup = true;
}
