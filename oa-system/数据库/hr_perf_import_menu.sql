-- BIZ-OPT-10（结果导入）：新增「结果导入」按钮权限 hr:performance:import
--
-- 为什么不复用 hr:performance:plan（方案维护）：导入直接把考核表写成终态、并决定薪资系数，
-- 与「配置方案/指标」是两种风险等级。方案维护通常给 HR 专员，导入历史数据应当更谨慎，
-- 需要时可以只授给人事经理。
--
-- 菜单 id 287 紧接现有的 286（自助服务）；sort=7 排在绩效管理下现有 6 个按钮之后。
-- 执行后需重算菜单数（超管角色菜单总数 141 → 142）。
--
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p oa_system < hr_perf_import_menu.sql

INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, sort, visible, status)
VALUES (287, '结果导入', 'hr:performance:import', 263, 3, 7, 1, 1);

INSERT INTO oa_role_menu (role_id, menu_id) VALUES (1, 287), (88, 287), (4, 287);
