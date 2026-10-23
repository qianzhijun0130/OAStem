-- =============================================================
-- 人力资源 P1 薪资档案基线数据（2026-10-23）
--   背景：薪资档案表为空 → 月度核算直接报「以下员工没有生效薪资档案」，
--        绩效→薪酬联动（绩效工资 = 绩效基数 × 绩效系数）也无从验证。
--   口径：每个在职员工一条生效档案，生效日期统一取 2026-01-01
--        （核算只认「effective_date <= 核算月末」的档案，取入职日会让
--          核算当月找不到档案）。
--        基数只做「能跑通核算」的值，不代表真实薪资。
-- =============================================================
USE oa_system;

INSERT INTO oa_salary_profile
    (user_id, effective_date, base_salary, post_salary, perf_salary,
     allowance, social_base, fund_base, city, special_deduct, change_reason, is_deleted)
SELECT u.id,
       '2026-01-01',
       10000.00, 2000.00, 3000.00,
       500.00, 15000.00, 15000.00, '北京', 0.00, '基线建档', 0
FROM oa_user u
WHERE u.is_deleted = 0
  AND u.employment_status IN (2, 3, 4)
  AND NOT EXISTS (
        SELECT 1 FROM oa_salary_profile p
        WHERE p.user_id = u.id AND p.is_deleted = 0
  );

-- 回填：员工直属上级（档案建档时未填 → 绩效「上级评分」链路断链）
--   口径：取本部门负责人（oa_department.leader_id），自己是负责人则留空
UPDATE oa_user u
JOIN oa_department d ON d.id = u.dept_id
SET u.direct_leader_id = d.leader_id
WHERE u.is_deleted = 0
  AND u.direct_leader_id IS NULL
  AND d.leader_id IS NOT NULL
  AND d.leader_id <> u.id;

-- =============================================================
-- 说明：
--   · 上面的档案基数是「演示值」，真实薪资由 HR 在系统里维护；
--   · direct_leader_id 回填让「上级评分」有明确责任人，
--     绩效模块本身也有「部门负责人兜底」（见 PerfCycleServiceImpl#resolveLeader）；
--   · 两段都写成幂等：重复执行不会产生重复档案、不会覆盖已有上级。
-- =============================================================
