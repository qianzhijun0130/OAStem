-- ============================================================
-- 薪资核算：新增「折算系数 / 折算后标准工资」两列（BIZ-OPT-04 展示项）
-- 背景：入职/离职当月按在职天数折算后，应发会小于标准工资；
--       若不在工资条上显示折算依据，员工无法理解金额差异，HR 也无法快速核对。
-- 幂等：information_schema 判存在后再 ALTER，可重复执行。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p oa_system < hr_salary_proration_columns.sql
-- ============================================================

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'oa_salary_record'
       AND column_name = 'proration_factor') = 0,
  'ALTER TABLE oa_salary_record ADD COLUMN proration_factor DECIMAL(6,4) NULL COMMENT ''在职天数折算系数（1=不折算）'' AFTER allowance',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'oa_salary_record'
       AND column_name = 'prorated_standard') = 0,
  'ALTER TABLE oa_salary_record ADD COLUMN prorated_standard DECIMAL(12,2) NULL COMMENT ''折算后标准工资'' AFTER proration_factor',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
