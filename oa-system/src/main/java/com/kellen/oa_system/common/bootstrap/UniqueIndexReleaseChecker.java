package com.kellen.oa_system.common.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 逻辑删除「唯一索引」自检：启动时扫描数据库，把<b>未受唯一性保护</b>的表暴露到日志。
 *
 * <p>背景：业务表把唯一索引建在编码列上，而索引若<b>不含</b>删除标记，软删除后原值会被
 * 「幽灵行」长期占住，同值无法重建。本系统的做法是把唯一索引改为
 * {@code (编码, active_flag)}——{@code active_flag} 是生成列，
 * <b>存活行取 1、已删行取 NULL</b>，利用「MySQL 唯一索引允许多个 NULL」让已删行自动让位。
 * （等价于 PostgreSQL 的 partial unique index {@code WHERE is_deleted = 0}。）</p>
 *
 * <p><b>判据是结构性的，不需要人工登记</b>：表有 {@code is_deleted} 列时，它的每个单列唯一索引
 * 都必须是「已加保护」的形态——即索引列里含 {@code active_flag}。因此：</p>
 * <ul>
 *   <li>索引列含 {@code active_flag} → 已受保护</li>
 *   <li>单列唯一索引且不含 {@code active_flag} → <b>风险，告警</b></li>
 *   <li>多列唯一索引（如 {@code uk_user_role(user_id, role_id)}）→ 组合键天然规避，跳过</li>
 * </ul>
 *
 * <p>这一点相比上一版的「白名单」是实质改进：新增一张风险表时，<b>只要忘了加保护，启动就会被拦下</b>，
 * 不依赖任何人记得来登记。相应地也不再有「已知未接入」这个中间态——要么受保护，要么告警。</p>
 *
 * <p><b>只读不改</b>：仅查询 {@code information_schema} 并输出日志，绝不改动任何业务数据。
 * 与 {@link com.kellen.oa_system.workflow.bootstrap.FlowKeyConsistencyChecker} 同一约定。</p>
 */
@Slf4j
@Component
@Order(UniqueIndexReleaseChecker.ORDER)
@RequiredArgsConstructor
public class UniqueIndexReleaseChecker implements ApplicationRunner {

    /** 排在流程播种与流程体检（100 / 200）之后，避免日志交错 */
    public static final int ORDER = 300;

    /** 逻辑删除列：表有它才说明走的是软删除 */
    private static final String DELETED_COLUMN = "is_deleted";

    /** 唯一性保护列：生成列，存活=1、已删=NULL；唯一索引含它才算受保护 */
    private static final String GUARD_COLUMN = "active_flag";

    private static final String INDEX_SQL = """
            SELECT s.TABLE_NAME AS tbl, s.INDEX_NAME AS idx, s.COLUMN_NAME AS col
            FROM information_schema.STATISTICS s
            WHERE s.TABLE_SCHEMA = DATABASE()
              AND s.NON_UNIQUE = 0
              AND s.INDEX_NAME <> 'PRIMARY'
            ORDER BY s.TABLE_NAME, s.INDEX_NAME, s.SEQ_IN_INDEX""";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<TableIndexes> tables;
        try {
            tables = inspect();
        } catch (Exception e) {
            // 自检失败不能影响应用启动；但必须留痕，避免「静默失去保护」
            log.warn("逻辑删除唯一索引自检未能完成（不影响启动）：{}", e.getMessage());
            return;
        }

        if (tables.isEmpty()) {
            log.info("逻辑删除唯一索引自检通过：未发现使用逻辑删除的带唯一索引表");
            return;
        }

        Map<String, String> risky = new LinkedHashMap<>();
        int ok = 0;
        for (TableIndexes table : tables) {
            // 判据逐索引用：同一张表可能既有已保护的索引，也有遗漏的索引，必须逐个检查
            List<String> naked = table.nakedSingleColumnIndexes();
            if (naked.isEmpty()) {
                ok++;
            } else {
                risky.put(table.table(), table.table() + " 缺少保护的单列唯一索引 " + naked);
            }
        }

        if (risky.isEmpty()) {
            log.info("逻辑删除唯一索引自检通过：{} 张表的单列唯一索引均已加 {} 保护", ok, GUARD_COLUMN);
            return;
        }
        log.warn("检测到 {} 张表的唯一索引未加逻辑删除保护：{}", risky.size(), risky.values());
        log.warn("影响：软删除后原编码会被占死，同编码无法重建；错误提示对使用者不可理解");
        log.warn("处置：为该表增加生成列 {} TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL，"
                + "并把唯一索引改为 (编码, {})；可运行的 ALTER 样例见 oa-system/数据库/*.sql",
                GUARD_COLUMN, GUARD_COLUMN);
    }

    /**
     * 扫描出「有 {@code is_deleted} 列 且 至少有一个单列唯一索引」的表，逐表汇总其唯一索引形态。
     *
     * <p>注意判据是<b>单列唯一索引缺保护</b>，而不是「有没有单列唯一索引」——已改造的表索引变成两列，
     * 天然不再命中；未改造的表才会被列出来。</p>
     */
    private List<TableIndexes> inspect() {
        Set<String> deletedTables = new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = ?",
                String.class, DELETED_COLUMN));
        if (deletedTables.isEmpty()) {
            return List.of();
        }

        // table -> index -> 列（按 SEQ_IN_INDEX 顺序）
        Map<String, Map<String, List<String>>> grouped = new LinkedHashMap<>();
        jdbcTemplate.query(INDEX_SQL, rs -> {
            String table = rs.getString("tbl");
            if (!deletedTables.contains(table)) {
                return;
            }
            grouped.computeIfAbsent(table, k -> new LinkedHashMap<>())
                    .computeIfAbsent(rs.getString("idx"), k -> new ArrayList<>())
                    .add(rs.getString("col"));
        });

        List<TableIndexes> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : grouped.entrySet()) {
            result.add(new TableIndexes(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /** 一张表的唯一索引形态 */
    private record TableIndexes(String table, Map<String, List<String>> indexes) {

        /** 未受保护的单列唯一索引（组合索引由组合键天然规避，已受保护的含保护列） */
        List<String> nakedSingleColumnIndexes() {
            return indexes.entrySet().stream()
                    .filter(e -> e.getValue().size() == 1 && !e.getValue().contains(GUARD_COLUMN))
                    .map(e -> e.getKey() + "(" + String.join(", ", e.getValue()) + ")")
                    .toList();
        }
    }
}
