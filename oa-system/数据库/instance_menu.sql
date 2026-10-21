-- 流程实例菜单（审批中心新增）：全量实例查看与管理
-- 说明：本脚本与 flow_menu.sql 风格一致，用于重建库时复现菜单。
--       实际在本机通过菜单管理接口录入（页面 id=218 / 终止按钮 219 / 删除按钮 220），
--       此处用同样 id 落 SQL 以便幂等重跑；若目标库 218~220 已被占用请先核对。
SET NAMES utf8mb4;
USE oa_system;

-- 1) 页面菜单：挂在「审批中心」（id=50）下，排序 6，排在「我的发起」之后
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, icon, sort, visible, status)
VALUES (218, '流程实例', 'system:approval:instance', 50, 2, '/workflow/instance', 'workflow/instance/index', 'Ticket', 6, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), menu_code = VALUES(menu_code),
                        parent_id = VALUES(parent_id), menu_type = VALUES(menu_type), path = VALUES(path),
                        component = VALUES(component), icon = VALUES(icon),
                        sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 2) 按钮权限（visible=0，不进侧边栏；供前端 v-permission 与后端 @PreAuthorize 校验）
INSERT INTO oa_menu (id, menu_name, menu_code, parent_id, menu_type, sort, visible, status)
VALUES
  ('终止实例', 'system:approval:terminate', 218, 3, 1, 0, 1),
  ('删除实例', 'system:approval:delete',   218, 3, 2, 0, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status);

-- 3) 授权：超管隐式拥有，此处仅补各业务经理（普通员工/专员不授予）
--    具体授权走角色菜单接口（全量覆盖 + 自动补祖先）；以下列出应授予的角色 code 供核对
--    HR_MANAGER / FINANCE_MANAGER / TECH_MANAGER / MARKET_MANAGER / TEST_M02_MGR
SELECT m.id, m.menu_name, m.menu_code, m.menu_type, m.parent_id, m.path, m.component, m.visible
FROM oa_menu m WHERE m.menu_code IN ('system:approval:instance', 'system:approval:terminate', 'system:approval:delete')
ORDER BY m.id;
