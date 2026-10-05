-- 第四阶段：系统字典菜单与按钮权限
-- 说明：工作台菜单（id=1）与个人中心均为「所有登录用户可用」，不在此配置；
--       个人中心走前端静态路由（后端菜单树只下发 visible=1，不适合承载隐藏路由）。
--
-- ⚠️ id 选择约定：不要随手写 id，先确认该 id 未被占用。
--    当前 oa_menu 已用 id：1、10~16、20~25、30~34、40~44、50~55、56~65（审批按钮）、70（本脚本）。
--    本脚本曾误用 id=60 与 flow_menu.sql 的「发起审批」按钮主键冲突，已迁移至 id=70，
--    修复脚本见同目录 fix_menu_id60_conflict.sql。
USE oa_system;
SET NAMES utf8mb4;

-- 1) 页面菜单：挂在「系统管理」（id=10）下，排序 5，排在部门管理之后
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES (70, '系统字典', 'system:dict', 10, 2, '/system/dict', 'system/dict/index', 'Collection', 5, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), menu_code = VALUES(menu_code),
                        parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type), path = VALUES(path),
                        component = VALUES(component), icon = VALUES(icon),
                        sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 2) 按钮权限（visible=0，不进侧边栏）
-- 注意：权限码要与 Controller 上的 @PreAuthorize 完全一致，否则接口一律 403
INSERT INTO oa_menu (menu_name, menu_code, parent_id, menu_type, sort, visible, status)
VALUES
  ('字典查询', 'system:dict:query',  70, 3, 1, 0, 1),
  ('字典新增', 'system:dict:add',    70, 3, 2, 0, 1),
  ('字典编辑', 'system:dict:edit',   70, 3, 3, 0, 1),
  ('字典删除', 'system:dict:delete', 70, 3, 4, 0, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status);

-- 3) 授权：字典属于系统配置，先只给超级管理员（role_id=1）。
--    如需让其他角色也能维护字典，把下面的 role_id 换成对应角色 id 即可。
INSERT IGNORE INTO oa_role_menu (role_id, menu_id)
SELECT 1, m.id FROM oa_menu m
WHERE m.menu_code LIKE 'system:dict%';

SELECT m.id, m.menu_name, m.menu_code, m.menu_type, m.parent_id, m.path, m.visible
FROM oa_menu m WHERE m.menu_code LIKE 'system:dict%' ORDER BY m.id;
