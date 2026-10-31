-- =====================================================================
-- BIZ-OPT-09 步骤 1：身份证盲索引列
--
-- 为什么需要这一列：
--   oa_user.id_card 走 AES-256-GCM 字段级加密，每次加密用随机 IV，
--   同一个身份证号两次写入得到不同密文。于是「该身份证是否已建档」
--   无法用 `WHERE id_card = ?` 判断——密文既不等于任何可比对的值，
--   也没法在 SQL 里解密比对。
--
-- 做法：另存一列 HMAC-SHA256(密钥, 规范化明文) 作为「盲索引」。
--   写入时同步计算，查重时用同样方式算一遍再比，全程不落明文。
--   带密钥是必须的：身份证号空间小、可穷举，裸 SHA-256 等于明文。
--
-- 约定：
--   1. 只建普通索引，不建唯一索引 —— 存量脏数据（同一人两份档案）需要
--      先由 HR 清理，直接加唯一索引会让服务起不来；
--   2. 换 security.crypto.key 后本列全部失效，必须「解密 → 重算哈希」迁移；
--   3. 存量回填由应用启动时的 IdCardHashBackfill 完成（见该类注释）。
--
-- 执行：mysql --default-character-set=utf8mb4 -u? -p? oa_system < 本文件
-- =====================================================================

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE oa_user ADD COLUMN id_card_hash CHAR(64) NULL COMMENT ''身份证盲索引 HMAC-SHA256(去空白+大写)，用于等值查重，不可反推'' AFTER id_card',
        'SELECT ''id_card_hash 已存在，跳过'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'oa_user'
      AND COLUMN_NAME = 'id_card_hash'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE oa_user ADD INDEX idx_user_id_card_hash (id_card_hash)',
        'SELECT ''idx_user_id_card_hash 已存在，跳过'''
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'oa_user'
      AND INDEX_NAME = 'idx_user_id_card_hash'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
