-- 第三阶段「审批中心」菜单与按钮权限（目录 + 5 个页面 + 按钮权限码）
SET NAMES utf8mb4;

-- 1) 目录：审批中心
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES (50, '审批中心', 'workflow', 0, 1, '/workflow', 'Layout', 'Promotion', 10, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type), path = VALUES(path),
                        component = VALUES(component), icon = VALUES(icon),
                        sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 2) 页面菜单
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES
  (51, '流程模板', 'system:flow',        50, 2, '/workflow/definition', 'workflow/definition/index', 'Share',      1, 1, 1),
  (52, '发起申请', 'system:approval:apply', 50, 2, '/workflow/apply',      'workflow/apply/index',      'EditPen',    2, 1, 1),
  (53, '我的待办', 'system:approval:todo',  50, 2, '/workflow/todo',       'workflow/todo/index',       'Clock',      3, 1, 1),
  (54, '我的已办', 'system:approval:done',  50, 2, '/workflow/done',       'workflow/done/index',       'Select',     4, 1, 1),
  (55, '我的发起', 'system:approval:mine',  50, 2, '/workflow/mine',       'workflow/mine/index',       'Document',   5, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type), path = VALUES(path),
                        component = VALUES(component), icon = VALUES(icon),
                        sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 3) 按钮权限（visible=0，不进侧边栏）
INSERT INTO oa_menu (menu_name, menu_code, parent_id, menu_type, sort, visible, status)
VALUES
  ('模板查询',   'system:flow:query',   51, 3, 1, 0, 1),
  ('模板新增',   'system:flow:add',     51, 3, 2, 0, 1),
  ('模板编辑',   'system:flow:edit',    51, 3, 3, 0, 1),
  ('模板删除',   'system:flow:delete',  51, 3, 4, 0, 1),
  ('发起审批',   'system:approval:start',    52, 3, 1, 0, 1),
  ('审批处理',   'system:approval:complete', 53, 3, 1, 0, 1),
  ('审批转办',   'system:approval:transfer', 53, 3, 2, 0, 1),
  ('审批详情',   'system:approval:query',    53, 3, 3, 0, 1),
  ('撤销申请',   'system:approval:withdraw', 55, 3, 1, 0, 1),
  ('发起详情',   'system:approval:mine:query', 55, 3, 2, 0, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status);

-- 4) 授权给超管角色（role_id=1，SUPER_ADMIN）
INSERT IGNORE INTO oa_role_menu (role_id, menu_id)
SELECT 1, m.id FROM oa_menu m
WHERE m.menu_code LIKE 'workflow%'
   OR m.menu_code LIKE 'system:flow%'
   OR m.menu_code LIKE 'system:approval%';

-- 5) 同时授权给其他已存在角色（保证非超管可用，按需可再收敛）
INSERT IGNORE INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM oa_role r, oa_menu m
WHERE r.role_code <> 'SUPER_ADMIN'
  AND (m.menu_code LIKE 'workflow%'
    OR m.menu_code LIKE 'system:flow%'
    OR m.menu_code LIKE 'system:approval%');

SELECT m.id, m.menu_name, m.menu_code, m.menu_type, m.parent_id, m.path, m.component, m.visible
FROM oa_menu m WHERE m.menu_code LIKE 'workflow%' OR m.menu_code LIKE 'system:flow%' OR m.menu_code LIKE 'system:approval%'
ORDER BY m.id;
