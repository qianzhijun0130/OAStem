-- ══════════════════════════════════════════════════════════════
-- 系统管理 > 在线用户（菜单 + 按钮权限 + 超管授权）
--
-- 幂等：先按 menu_code 清掉旧记录再插入，可重复执行。
-- 编号 227/228 接在现有最大 id 226（抄送任务）之后；sort=6 排在「系统字典」之后。
--
-- 说明：菜单只是入口与权限载体，真正的会话数据在 Redis（oastem:auth:online:*），
-- 本脚本不涉及任何业务表结构变更。
-- ══════════════════════════════════════════════════════════════

USE oa_system;

-- 1) 清理旧记录（含授权关系），保证可重复执行
DELETE FROM oa_role_menu
WHERE menu_id IN (SELECT id FROM oa_menu WHERE menu_code LIKE 'system:online%');

DELETE FROM oa_menu WHERE menu_code LIKE 'system:online%';

-- 2) 菜单：系统管理 > 在线用户
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES (227, '在线用户', 'system:online:list', 10, 2, '/system/online', 'system/online/index', 'Activity', 6, 1, 1);

-- 3) 按钮：强制下线（不生成路由，仅作权限码）
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES (228, '强制下线', 'system:online:kick', 227, 3, NULL, NULL, NULL, 1, 1, 1);

-- 4) 授权给超级管理员（role_id = 1）
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT 1, id FROM oa_menu WHERE menu_code LIKE 'system:online%';
