-- HR / HR_MANAGER 的数据范围必须为「全部」。
-- 背景：BE-OPT-02 给薪资列表、假勤导出、绩效列表补上 @DataScope 后，
-- 这两个角色若仍是 data_scope=2（本部门），HR 会看不到其他部门的工资与假勤，
-- 与"人事负责全员"的职责冲突。执行后需 HR 账号重新登录（JWT 里带的是快照）。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p oa_system < fix_hr_role_data_scope.sql
UPDATE oa_role SET data_scope = 1 WHERE role_code IN ('HR', 'HR_MANAGER') AND is_deleted = 0;
