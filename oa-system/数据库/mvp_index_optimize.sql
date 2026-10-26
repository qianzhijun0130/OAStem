-- ============================================================
-- 高频查询复合索引补齐（BE-OPT-06）
--
-- 问题：现有索引覆盖了唯一约束与单列查询，但「状态 + 时间区间」「处理人 + 动作 + 时间排序」
--       这类高频组合没有复合索引，优化器只能走低选择性单列索引再回表，或在排序时 filesort。
-- 原则：只补缺失的复合索引，不改历史建表脚本；幂等（同名索引存在则跳过）。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p oa_system < mvp_index_optimize.sql
-- ============================================================

-- 1) 假勤集合：日结/月结每次必查（leave_status = 2 是多数值，单列索引选择性极低）
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'oa_leave'
                   AND index_name = 'idx_leave_status_start') = 0,
  'ALTER TABLE oa_leave ADD INDEX idx_leave_status_start (leave_status, start_time, end_time)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) 出差集合：idx_trip_user 首列是 user_id，本查询无 user_id 条件，完全用不上
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'oa_business_trip'
                   AND index_name = 'idx_trip_status_start') = 0,
  'ALTER TABLE oa_business_trip ADD INDEX idx_trip_status_start (trip_status, start_date)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 3) 已办列表：handler_id 可定位，但 action IN + ORDER BY create_time 触发 filesort
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'oa_approval_record'
                   AND index_name = 'idx_handler_action_time') = 0,
  'ALTER TABLE oa_approval_record ADD INDEX idx_handler_action_time (handler_id, action, create_time)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 4) 薪资按「月份 + 状态」批量推进（idx_status 只有 4 个值域，选择性极低）
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'oa_salary_record'
                   AND index_name = 'idx_month_status_user') = 0,
  'ALTER TABLE oa_salary_record ADD INDEX idx_month_status_user (month, status, user_id)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 5) 绩效归档周期：status + 时间区间 + ORDER BY end_date DESC，此前无任何可用索引
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'oa_perf_cycle'
                   AND index_name = 'idx_status_end') = 0,
  'ALTER TABLE oa_perf_cycle ADD INDEX idx_status_end (status, end_date)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 6) 我的申请：applicant_id + status + ORDER BY id DESC
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'oa_approval_instance'
                   AND index_name = 'idx_applicant_status_id') = 0,
  'ALTER TABLE oa_approval_instance ADD INDEX idx_applicant_status_id (applicant_id, status, id)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 复核：应看到 6 条新索引
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND index_name IN ('idx_leave_status_start','idx_trip_status_start','idx_handler_action_time',
                      'idx_month_status_user','idx_status_end','idx_applicant_status_id')
 GROUP BY table_name, index_name ORDER BY table_name;
