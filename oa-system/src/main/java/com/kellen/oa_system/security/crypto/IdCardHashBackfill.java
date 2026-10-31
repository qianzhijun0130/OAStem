package com.kellen.oa_system.security.crypto;

import com.kellen.oa_system.common.util.IdCardUtil;
import com.kellen.oa_system.security.crypto.mapper.SensitiveDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 身份证盲索引的存量回填（幂等）。
 *
 * <p>为什么需要回填而不是建列就完事：{@code id_card_hash} 是后加的列，
 * 历史员工的身份证号早已入库（密文），但哈希是空的。不回填的话，
 * 「该身份证是否已建档」对老员工永远返回「未建档」，导入时同一个人会被登记两次，
 * 于是薪资/考勤各挂一份档案——这正是本次要消除的问题本身。</p>
 *
 * <p>为什么在应用内做而不是写 SQL：{@code id_card} 是 AES-GCM 密文，
 * 每次加密随机 IV，SQL 无法解密，也无法在库里算出同一摘要。
 * 必须先由应用解密再算 HMAC。</p>
 *
 * <p>顺序：排在 {@link SensitiveDataMigrator} 之后。虽然两者互不依赖
 * （{@link AesCrypto#decrypt(String)} 对明文原样返回，先算后算结果一致），
 * 但显式排后可以保证日志里「先加密、后补哈希」的因果清楚。</p>
 *
 * <p>失败策略：单行失败只记日志、跳过，不中断启动。原因同加密迁移——
 * 一条脏数据不该让整个服务起不来；该行下次启动会重试。</p>
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class IdCardHashBackfill implements ApplicationRunner {

    private final SensitiveDataMapper sensitiveDataMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Map<String, Object>> rows = sensitiveDataMapper.selectIdCardsMissingHash();
            if (rows == null || rows.isEmpty()) {
                log.info("身份证盲索引回填：无待处理数据");
                return;
            }
            int filled = 0;
            int failed = 0;
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("id");
                Object valueObj = row.get("v");
                if (!(idObj instanceof Number) || valueObj == null) {
                    continue;
                }
                long id = ((Number) idObj).longValue();
                try {
                    // 密文先解密；历史明文由 decrypt 原样返回
                    String plain = AesCrypto.get().decrypt(String.valueOf(valueObj));
                    String hash = IdCardUtil.hash(plain);
                    if (hash == null) {
                        continue;
                    }
                    sensitiveDataMapper.updateIdCardHash(id, hash);
                    filled++;
                } catch (Exception e) {
                    // 典型原因：密钥换过，旧密文解不开。此时该行需要人工处理，
                    // 不能悄悄跳过——所以用 warn 逐条记 id，便于导出待修清单。
                    failed++;
                    log.warn("身份证盲索引回填失败（该行需人工核对）: userId={}, 原因={}", id, e.getMessage());
                }
            }
            if (filled > 0 || failed > 0) {
                log.warn("身份证盲索引回填完成：成功 {} 条，失败 {} 条", filled, failed);
            }
        } catch (Exception e) {
            log.error("身份证盲索引回填整体失败，下次启动将重试", e);
        }
    }
}
