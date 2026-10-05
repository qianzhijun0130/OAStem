-- 一次性修复：stage4_menu.sql 固定 id=60 与 flow_menu.sql 的「发起审批」按钮（id=60）主键冲突
-- 症状：id=60 被覆盖成「系统字典」，但 menu_code 残留为 system:approval:start；
--       原「发起审批」按钮权限丢失，且「系统字典」的 menu_code 错误。
-- 处置：恢复 id=60 为「发起审批」按钮；「系统字典」页面菜单迁移至空闲 id=70；字典按钮权限改挂 70。
USE oa_system;
SET NAMES utf8mb4;

-- 1) 恢复 id=60 为「发起审批」按钮（parent_id=52 发起申请页面）
UPDATE oa_menu
SET menu_name = '发起审批',
    menu_code = 'system:approval:start',
    parent_id = 52,
    menu_type = 3,
    path      = NULL,
    component = NULL,
    icon      = NULL,
    sort      = 1,
    visible   = 0,
    status    = 1
WHERE id = 60;

-- 2) 「系统字典」页面菜单迁移到空闲 id=70（挂在「系统管理」id=10 下）
DELETE FROM oa_menu WHERE id = 70;
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES (70, '系统字典', 'system:dict', 10, 2, '/system/dict', 'system/dict/index', 'Collection', 5, 1, 1);

-- 3) 字典按钮权限改挂到新的页面菜单 id=70
UPDATE oa_menu SET parent_id = 70 WHERE menu_code LIKE 'system:dict:%';

-- 4) 授权超管（role_id=1）
INSERT IGNORE INTO oa_role_menu (role_id, menu_id)
SELECT 1, m.id FROM oa_menu m WHERE m.menu_code LIKE 'system:dict%';

-- 校验
SELECT id, menu_name, menu_code, menu_type, parent_id, visible
FROM oa_menu WHERE id IN (52, 60, 66, 67, 68, 69, 70) ORDER BY id;
