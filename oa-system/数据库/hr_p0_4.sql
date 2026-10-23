-- =====================================================================
-- 人力资源 P0 收尾补丁（2026-10-23）
--   1) 考勤状态字典补「出差」(6)：日结会把出差日标成 6，字典缺这一项界面会显示成数字
--   2) 新增按钮权限「导出汇总」(hr:attendance:export)
--   3) 授权：SUPER_ADMIN / HR / HR_MANAGER
-- 幂等：可重复执行（NOT EXISTS 判断）
-- =====================================================================
USE oa_system;

-- ---------- 1) 字典：考勤状态补「出差」 ----------
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_attendance_status', '出差', '6', 6, 1, 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data
                    WHERE dict_type = 'hr_attendance_status' AND item_value = '6' AND is_deleted = 0);

-- ---------- 2) 按钮权限：考勤导出 ----------
-- oa_menu 是物理删除表（无 is_deleted），故只判断 id 是否存在
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 254, 233, '导出汇总', 3, NULL, 'hr:attendance:export', NULL, NULL, 6, 1, 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 254);

-- ---------- 3) 授权给人事相关角色 ----------
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, 254
  FROM oa_role r
 WHERE r.role_code IN ('SUPER_ADMIN', 'HR', 'HR_MANAGER')
   AND r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 254);
