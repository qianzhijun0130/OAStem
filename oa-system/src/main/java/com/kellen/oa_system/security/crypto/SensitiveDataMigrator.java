package com.kellen.oa_system.security.crypto;

import com.kellen.oa_system.security.crypto.mapper.SensitiveDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 存量明文敏感字段的一次性加密迁移。
 *
 * <p>幂等：只处理<b>不带 {@code ENC:} 前缀</b>的值，已加密的行一律跳过，
 * 所以每次启动都跑也不会二次加密（二次加密会把密文再加密一次，读出来是乱码）。</p>
 *
 * <p>失败策略：单列迁移异常只记录错误、不中断启动。原因是此时应用已在运行，
 * 因个别脏数据导致整体起不来，比"部分行仍是明文"影响更大；
 * 未迁移成功的行会保持明文，可读可写，下次启动继续尝试。</p>
 */
@Slf4j
@Component
@Order
@RequiredArgsConstructor
public class SensitiveDataMigrator implements ApplicationRunner {

    private final SensitiveDataMapper sensitiveDataMapper;
    private final CryptoProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isMigrateOnStartup()) {
            log.info("敏感字段存量迁移已关闭（security.crypto.migrate-on-startup=false）");
            return;
        }
        int total = 0;
        total += migrate("oa_user.id_card",
                sensitiveDataMapper::selectRawIdCards, sensitiveDataMapper::updateIdCard);
        total += migrate("oa_employee_bank.bank_account",
                sensitiveDataMapper::selectRawBankAccounts, sensitiveDataMapper::updateBankAccount);
        total += migrate("oa_employee_bank.social_no",
                sensitiveDataMapper::selectRawSocialNos, sensitiveDataMapper::updateSocialNo);
        total += migrate("oa_employee_bank.fund_no",
                sensitiveDataMapper::selectRawFundNos, sensitiveDataMapper::updateFundNo);

        if (total > 0) {
            log.warn("敏感字段存量迁移完成：共加密 {} 个值（明细见各列日志）", total);
        } else {
            log.info("敏感字段存量迁移：无明文数据，跳过");
        }
    }

    private int migrate(String label,
                        java.util.function.Supplier<List<Map<String, Object>>> selector,
                        BiFunction<Long, String, Integer> updater) {
        try {
            List<Map<String, Object>> rows = selector.get();
            if (rows == null || rows.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("id");
                Object valueObj = row.get("v");
                if (!(idObj instanceof Number) || valueObj == null) {
                    continue;
                }
                String value = String.valueOf(valueObj);
                if (AesCrypto.isEncrypted(value)) {
                    continue;
                }
                updater.apply(((Number) idObj).longValue(), AesCrypto.get().encrypt(value));
                count++;
            }
            if (count > 0) {
                log.warn("敏感字段迁移 [{}]：加密 {} 个值", label, count);
            }
            return count;
        } catch (Exception e) {
            log.error("敏感字段迁移 [{}] 失败，该列可能仍为明文，下次启动将重试", label, e);
            return 0;
        }
    }
}
