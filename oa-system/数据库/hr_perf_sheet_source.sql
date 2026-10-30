-- BIZ-OPT-10（结果导入部分）：绩效结果区分「系统评分」与「导入」
--
-- 背景：历史绩效迁移（老系统/Excel 台账）导进来的分数，与系统里走完自评→上级评→校准→确认
-- 流程的结果，可信度不同。不标来源的话，员工看到一条 85 分无法判断是不是自己上司打的，
-- 申诉时 HR 也说不清这条从哪来。
--
-- 口径：source = 1 系统评分（默认，历史数据全部归此类）/ 2 结果导入
--
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p oa_system < hr_perf_sheet_source.sql

ALTER TABLE oa_perf_sheet
    ADD COLUMN source TINYINT NOT NULL DEFAULT 1
        COMMENT '结果来源：1系统评分 2结果导入' AFTER confirm_status;
