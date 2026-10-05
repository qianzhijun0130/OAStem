-- 第四阶段第二轮：公告中心 / 日志管理 菜单与按钮权限
--
-- ⚠️ id 分配约定：新增菜单前先确认空闲 id，禁止随手写。
--    当前 oa_menu 已占用：1、10~16、20~25、30~34、40~44、50~55、56~65、66~69、70。
--    本脚本使用 80~87（公告中心及按钮）与 90~98（日志管理及按钮）。
--    历史教训：stage4_menu.sql 曾误用 id=60，与 flow_menu.sql 的「发起审批」按钮主键冲突，
--    见同目录 fix_menu_id60_conflict.sql。
USE oa_system;
SET NAMES utf8mb4;

-- ============ 一、公告中心 ============

-- 1) 目录
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES (80, '公告中心', 'notice', 0, 1, '/notice', 'Layout', 'Bell', 20, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), menu_code = VALUES(menu_code),
                        parent_id = VALUES(parent_id), menu_type = VALUES(menu_type),
                        path = VALUES(path), component = VALUES(component), icon = VALUES(icon),
                        sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 2) 页面菜单
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES
  (81, '公告管理', 'system:notice', 80, 2, '/notice/manage', 'notice/manage/index', 'EditPen', 1, 1, 1),
  (82, '我的公告', 'notice:my',      80, 2, '/notice/my',     'notice/my/index',     'ChatDotSquare', 2, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), menu_code = VALUES(menu_code),
                        parent_id = VALUES(parent_id), menu_type = VALUES(menu_type),
                        path = VALUES(path), component = VALUES(component), icon = VALUES(icon),
                        sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 3) 按钮权限（visible=0，不进侧边栏）
--    权限码必须与 Controller 上的 @PreAuthorize 完全一致，否则接口一律 403
INSERT INTO oa_menu (menu_name, menu_code, parent_id, menu_type, sort, visible, status)
VALUES
  ('公告查询', 'system:notice:query',   81, 3, 1, 0, 1),
  ('公告新增', 'system:notice:add',     81, 3, 2, 0, 1),
  ('公告编辑', 'system:notice:edit',    81, 3, 3, 0, 1),
  ('公告删除', 'system:notice:delete',  81, 3, 4, 0, 1),
  ('公告发布', 'system:notice:publish', 81, 3, 5, 0, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type), sort = VALUES(sort),
                        visible = VALUES(visible), status = VALUES(status);

-- ============ 二、日志管理 ============

-- 4) 目录
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES (90, '日志管理', 'log', 0, 1, '/log', 'Layout', 'Document', 30, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), menu_code = VALUES(menu_code),
                        parent_id = VALUES(parent_id), menu_type = VALUES(menu_type),
                        path = VALUES(path), component = VALUES(component), icon = VALUES(icon),
                        sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 5) 页面菜单
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES
  (91, '登录日志', 'system:login-log',     90, 2, '/log/login-log',     'system/login-log/index',     'Key',     1, 1, 1),
  (92, '操作日志', 'system:operation-log', 90, 2, '/log/operation-log', 'system/operation-log/index', 'Tickets', 2, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), menu_code = VALUES(menu_code),
                        parent_id = VALUES(parent_id), menu_type = VALUES(menu_type),
                        path = VALUES(path), component = VALUES(component), icon = VALUES(icon),
                        sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 6) 按钮权限
INSERT INTO oa_menu (menu_name, menu_code, parent_id, menu_type, sort, visible, status)
VALUES
  ('日志查询', 'system:login-log:query',       91, 3, 1, 0, 1),
  ('日志导出', 'system:login-log:export',      91, 3, 2, 0, 1),
  ('日志清理', 'system:login-log:clean',       91, 3, 3, 0, 1),
  ('日志查询', 'system:operation-log:query',   92, 3, 1, 0, 1),
  ('日志导出', 'system:operation-log:export',  92, 3, 2, 0, 1),
  ('日志清理', 'system:operation-log:clean',   92, 3, 3, 0, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type), sort = VALUES(sort),
                        visible = VALUES(visible), status = VALUES(status);

-- ============ 三、授权 ============

-- 7) 超级管理员：全部菜单与按钮
INSERT IGNORE INTO oa_role_menu (role_id, menu_id)
SELECT 1, m.id FROM oa_menu m
WHERE m.id BETWEEN 80 AND 98;

-- 8) 其他已存在角色：只授「公告中心」目录与「我的公告」
--    读公告是所有角色的基础能力；公告管理与日志属于管理职能，按需在角色管理里单独分配。
INSERT IGNORE INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM oa_role r, oa_menu m
WHERE r.role_code <> 'SUPER_ADMIN'
  AND m.menu_code IN ('notice', 'notice:my');

-- 校验
SELECT m.id, m.menu_name, m.menu_code, m.menu_type, m.parent_id, m.path, m.visible
FROM oa_menu m WHERE m.id BETWEEN 80 AND 98 ORDER BY m.id;
