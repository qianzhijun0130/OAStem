package com.kellen.oa_system.security.crypto;

import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加解密（AES-256-GCM）。
 *
 * <p>三个设计取舍，都是为了"上线后不会自己咬自己"：</p>
 * <ol>
 *   <li><b>密文带 {@code ENC:} 前缀</b>：读的时候能区分"密文"与"历史明文"，
 *       于是迁移脚本天然幂等，也不需要一次性停服改库。</li>
 *   <li><b>每次加密随机 IV，IV 拼在密文前面</b>：同样的身份证号两次写入得到不同密文，
 *       避免"密文相同 ⇒ 明文相同"的推断泄露。GCM 自带完整性校验，密文被篡改会直接解密失败，
 *       而不是悄悄返回错误数据。</li>
 *   <li><b>读时对非 {@code ENC:} 值原样返回</b>：迁移尚未覆盖的行仍能正常显示，
 *       不会因为"库里还有明文"就 500。</li>
 * </ol>
 *
 * <p>注意：GCM 属于认证加密，<b>密钥一旦更换，旧密文必然无法解密</b>。
 * 因此密钥必须纳入备份范围，换密钥需要配套的"解密—再加密"迁移流程。</p>
 */
public final class AesCrypto {

    /** 密文前缀，用于区分密文与历史明文，同时保证迁移幂等 */
    public static final String PREFIX = "ENC:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    /**
     * 全局实例。TypeHandler 由 MyBatis 反射创建、不受 Spring 管理，
     * 因此只能通过静态入口取到 Spring 装配好的加解密器。
     */
    private static volatile AesCrypto instance;

    private final SecretKeySpec secretKey;
    private final SecureRandom random = new SecureRandom();

    public AesCrypto(String rawKey) {
        if (!StringUtils.hasText(rawKey)) {
            throw new IllegalStateException(
                    "未配置敏感字段加密密钥：请在 application-local.properties 设置 security.crypto.key，"
                            + "或通过环境变量 CRYPTO_KEY 注入。禁止以明文存储身份证与银行卡号。");
        }
        this.secretKey = new SecretKeySpec(sha256(rawKey), "AES");
        // 启动期自检：密钥错、算法不可用等问题必须在此暴露，而不是等用户打开档案页才 500
        String probe = "crypto-self-check";
        if (!probe.equals(decrypt(encrypt(probe)))) {
            throw new IllegalStateException("敏感字段加密自检失败：加解密往返结果不一致，请检查 security.crypto.key");
        }
    }

    /** 装配全局实例（由 {@link CryptoBootstrap} 在启动时调用一次） */
    static void install(AesCrypto crypto) {
        instance = crypto;
    }

    /** TypeHandler 取全局实例；未装配即抛错，绝不静默走明文 */
    public static AesCrypto get() {
        AesCrypto crypto = instance;
        if (crypto == null) {
            throw new IllegalStateException("敏感字段加密组件尚未初始化（CryptoBootstrap 未执行）");
        }
        return crypto;
    }

    /** 是否已是密文（{@code ENC:} 前缀） */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 加密。空值原样返回；已是密文则原样返回，保证重复调用不会套娃加密。
     */
    public String encrypt(String plain) {
        if (!StringUtils.hasText(plain) || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    /**
     * 解密。空值与历史明文原样返回；密文解不开时抛错（说明密钥不对或密文被篡改），
     * 不做"返回 null 或原密文"的兜底——那会把数据错误伪装成数据缺失，比直接报错更难排查。
     */
    public String decrypt(String stored) {
        if (!StringUtils.hasText(stored) || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalStateException("密文长度异常");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "敏感字段解密失败：请确认 security.crypto.key 与写入时一致（更换密钥会导致历史密文不可解）", e);
        }
    }

    private static byte[] sha256(String rawKey) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("密钥派生失败", e);
        }
    }
}
