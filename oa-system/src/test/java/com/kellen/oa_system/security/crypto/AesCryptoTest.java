package com.kellen.oa_system.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感字段加密的口径测试。
 *
 * <p>重点不在"能加密"，而在几个容易出事的边界：
 * 重复加密会不会套娃、历史明文还能不能读、密文被改一个字符会不会静默返回错值、
 * 换了密钥是报错还是悄悄返回垃圾。</p>
 */
class AesCryptoTest {

    private static final String KEY = "unit-test-key-0123456789abcdef";
    private static final String ID_CARD = "110101199003071234";

    private final AesCrypto crypto = new AesCrypto(KEY);

    @Test
    @DisplayName("加解密往返还原原文，且密文带 ENC: 前缀")
    void roundTrip() {
        String cipher = crypto.encrypt(ID_CARD);
        assertTrue(cipher.startsWith(AesCrypto.PREFIX), "密文必须带 ENC: 前缀，否则无法与历史明文区分");
        assertNotEquals(ID_CARD, cipher);
        assertEquals(ID_CARD, crypto.decrypt(cipher));
    }

    @Test
    @DisplayName("同一明文两次加密得到不同密文（随机 IV），但都能解回原文")
    void randomIvProducesDifferentCipherText() {
        String first = crypto.encrypt(ID_CARD);
        String second = crypto.encrypt(ID_CARD);
        assertNotEquals(first, second, "相同明文产生相同密文会泄露「两条记录证件号相同」");
        assertEquals(ID_CARD, crypto.decrypt(first));
        assertEquals(ID_CARD, crypto.decrypt(second));
    }

    @Test
    @DisplayName("重复加密幂等：已加密的值不会再套一层")
    void encryptIsIdempotent() {
        String once = crypto.encrypt(ID_CARD);
        assertEquals(once, crypto.encrypt(once), "二次加密会让明文不可还原");
        assertEquals(ID_CARD, crypto.decrypt(once));
    }

    @Test
    @DisplayName("历史明文直接读取：非 ENC: 前缀原样返回，保证迁移期间不 500")
    void legacyPlainTextPassesThrough() {
        assertEquals(ID_CARD, crypto.decrypt(ID_CARD));
        assertFalse(AesCrypto.isEncrypted(ID_CARD));
    }

    @Test
    @DisplayName("空值处理：null 与空串原样返回，不抛异常")
    void nullAndBlankHandled() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
        assertEquals("", crypto.encrypt(""));
        assertEquals("", crypto.decrypt(""));
    }

    @Test
    @DisplayName("密文被篡改必须解密失败，不能返回错值")
    void tamperedCipherTextFails() {
        String cipher = crypto.encrypt(ID_CARD);
        // 改动 Base64 载荷的最后一个字符，破坏 GCM 认证标签
        char last = cipher.charAt(cipher.length() - 1);
        String tampered = cipher.substring(0, cipher.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    @DisplayName("换密钥后旧密文解密失败并给出明确提示")
    void wrongKeyFailsLoudly() {
        String cipher = crypto.encrypt(ID_CARD);
        AesCrypto other = new AesCrypto("another-key-abcdefghijklmnop");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> other.decrypt(cipher));
        assertTrue(ex.getMessage().contains("security.crypto.key"), "异常需指向密钥配置，便于运维定位");
    }

    @Test
    @DisplayName("密钥为空时拒绝构造，避免静默降级为明文存储")
    void blankKeyRejected() {
        assertThrows(IllegalStateException.class, () -> new AesCrypto(null));
        assertThrows(IllegalStateException.class, () -> new AesCrypto("  "));
    }

    @Test
    @DisplayName("任意长度密钥均可使用（内部 SHA-256 派生 256 位）")
    void anyLengthKeyWorks() {
        AesCrypto shortKey = new AesCrypto("abc");
        assertEquals(ID_CARD, shortKey.decrypt(shortKey.encrypt(ID_CARD)));
    }
}
