-- BIZ-OPT-08：补卡区分「漏卡认领」与「主动补卡」
--
-- 背景：原来补卡只有「每月 3 次」一个额度，但系统其实已经知道很多人的缺卡事实
--       （有排班 + 只有一次刷卡 → card_missing > 0）。这类补卡是员工认领既有事实，
--       不是凭空声明，占额度会导致「真实漏卡多的人反而补不了」。
--
-- 口径：card_type = 1 主动补卡（计入每月 3 次） / 2 漏卡补卡（不计入）
-- 历史数据默认 1，与改造前的行为一致（改造前的所有补卡都按主动补卡计额）。
--
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p oa_system < hr_card_supplement_type.sql

ALTER TABLE oa_card_supplement
    ADD COLUMN card_type TINYINT NOT NULL DEFAULT 1
        COMMENT '补卡类型：1主动补卡(计入月配额) 2漏卡补卡(系统已识别缺卡,不计配额)' AFTER direction;

-- 回填：改造前已存在的、且该日该方向确实是系统识别的缺卡，按漏卡归位
UPDATE oa_card_supplement c
    JOIN oa_attendance a
    ON a.user_id = c.user_id AND a.attendance_date = c.card_date AND a.is_deleted = 0
SET c.card_type = 2
WHERE (a.card_missing = 3
    OR (a.card_missing = 1 AND c.direction = 1)
    OR (a.card_missing = 2 AND c.direction = 2));
