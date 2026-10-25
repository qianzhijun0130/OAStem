-- ============================================================
-- 人力资源 B2：地方假别政策维护（按钮权限）
-- 说明：政策维护做成「假勤管理」页内的一个页签，不再单独开一级菜单，
--       因此这里只加按钮级权限码 hr:leave:policy（menu_type=3）。
-- 幂等：按主键与「不存在才插入」双重保护，可重复执行。
-- 执行方式（含中文，必须走 stdin，不能用 -e）：
--   mysql --default-character-set=utf8mb4 -uroot -p oa_system < hr_b2_leave_policy_menu.sql
-- ============================================================

-- ---------- 1) 按钮权限：假别政策 ----------
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 285, 234, '假别政策', 3, NULL, 'hr:leave:policy', NULL, NULL, 7, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 285);

-- 若历史库中 285 已被占用（例如其他分支插过菜单），退化为按 menu_code 插入
INSERT INTO oa_menu (parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 234, '假别政策', 3, NULL, 'hr:leave:policy', NULL, NULL, 7, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE menu_code = 'hr:leave:policy');

-- ---------- 2) 授权：超管 / 人事专员 / 人事经理 ----------
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM oa_role r
  JOIN oa_menu m ON m.menu_code = 'hr:leave:policy'
 WHERE r.role_code IN ('SUPER_ADMIN', 'HR', 'HR_MANAGER')
   AND r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
