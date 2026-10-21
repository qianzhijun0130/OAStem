-- ==========================================================
-- OA 系统 · 初始数据脚本
-- ==========================================================
-- 用途：在第一阶段建好的 25 张表基础上，写入可让系统跑起来的最小数据集
--        （公司 / 部门 / 角色 / 菜单 / 超管账号 / 权限关联）
-- 前提：已执行 oa_system_init.sql
-- 特性：可重复执行（先按固定 id 区间清理，再插入）
-- 注意：本文件不含任何生产级密钥，超管初始密码见下方注释
-- ==========================================================

SET NAMES utf8mb4;
USE `oa_system`;

-- ----------------------------------------------------------
-- 清理：仅清理本脚本负责的演示数据 id 区间，避免误删业务数据
-- ----------------------------------------------------------
DELETE FROM `oa_role_menu` WHERE `role_id` BETWEEN 1 AND 10;
DELETE FROM `oa_user_role` WHERE `user_id` BETWEEN 1 AND 10;
DELETE FROM `oa_menu`     WHERE `id` BETWEEN 1 AND 100;
DELETE FROM `oa_role`     WHERE `id` BETWEEN 1 AND 10;
DELETE FROM `oa_user`     WHERE `id` BETWEEN 1 AND 10;
DELETE FROM `oa_department` WHERE `id` BETWEEN 1 AND 10;
DELETE FROM `oa_company`  WHERE `id` BETWEEN 1 AND 10;

-- ----------------------------------------------------------
-- 1. 公司
-- ----------------------------------------------------------
INSERT INTO `oa_company`
    (`id`, `company_name`, `company_code`, `parent_id`, `sort`, `status`)
VALUES
    (1, '示例科技有限公司', 'DEMO', 0, 1, 1);

-- ----------------------------------------------------------
-- 2. 部门（id=1 为根部门，其余为二级部门）
-- ----------------------------------------------------------
INSERT INTO `oa_department`
    (`id`, `dept_name`, `dept_code`, `company_id`, `parent_id`, `dept_type`, `sort`, `status`)
VALUES
    (1, '示例科技有限公司', 'DEMO',      1, 0, 1, 1, 1),
    (2, '技术部',         'DEMO-TECH', 1, 1, 2, 1, 1),
    (3, '人事部',         'DEMO-HR',   1, 1, 2, 2, 1),
    (4, '财务部',         'DEMO-FIN',  1, 1, 2, 3, 1),
    (5, '市场部',         'DEMO-MKT',  1, 1, 2, 4, 1);

-- ----------------------------------------------------------
-- 3. 角色（对应项目计划中的 5 个标准角色）
--    role_type: 1系统角色 2业务角色
--    data_scope: 1全部 2本部门 3仅本人
-- ----------------------------------------------------------
INSERT INTO `oa_role`
    (`id`, `role_name`, `role_code`, `role_type`, `data_scope`, `description`, `sort`, `status`)
VALUES
    (1, '超级管理员', 'SUPER_ADMIN', 1, 1, '拥有系统全部功能权限，负责系统配置与数据维护', 1, 1),
    (2, '部门管理员', 'DEPT_ADMIN',  1, 2, '拥有本部门人员管理、本部门审批查看权限',       2, 1),
    (3, '普通员工',   'EMPLOYEE',    1, 3, '发起审批、查看公告、管理个人信息',             3, 1),
    (4, 'HR专员',     'HR',          2, 2, '员工信息管理、人事类审批归档、组织架构查看',   4, 1),
    (5, '财务专员',   'FINANCE',     2, 2, '报销、采购类审批财务节点权限',                 5, 1);

-- ----------------------------------------------------------
-- 4. 超级管理员账号
--    账号：admin
--    初始密码：Admin@123456（BCrypt strength=10，首次登录后应立即修改）
--    密码哈希对应的明文在生产环境必须更换，禁止沿用此默认值
-- ----------------------------------------------------------
INSERT INTO `oa_user`
    (`id`, `username`, `password`, `real_name`, `nickname`, `gender`, `company_id`, `dept_id`,
     `position`, `work_number`, `entry_date`, `sort`, `status`)
VALUES
    (1, 'admin',
     '$2a$10$wLcAC/S982y5WXPkKOsmley6OasTDPgmQ.nsycNG7V9nrRyWJHX8K',
     '系统管理员', '超级管理员', 1, 1, 1, '系统管理员', 'A001', CURDATE(), 1, 1);

-- ----------------------------------------------------------
-- 5. 用户-角色关联
-- ----------------------------------------------------------
INSERT INTO `oa_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- ----------------------------------------------------------
-- 6. 菜单与按钮权限
--    menu_type: 1目录 2菜单 3按钮
--    visible=0 的菜单为「已规划但页面未实现」，前端不渲染，避免路由指向空组件
-- ----------------------------------------------------------
INSERT INTO `oa_menu`
    (`id`, `menu_name`, `menu_code`, `parent_id`, `menu_type`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
VALUES
    -- 工作台
    (1,  '工作台',   'dashboard',        0, 2, '/dashboard',    'dashboard/index',        'House',      0, 1, 1),

    -- 系统管理目录
    (10, '系统管理', 'system',           0, 1, '/system',       'Layout',                 'Settings',        90, 1, 1),

    -- 用户管理 + 按钮
    (11, '用户管理', 'system:user',     10, 2, '/system/user',  'system/user/index',      'User',           1, 1, 1),
    (12, '用户查询', 'system:user:query',  11, 3, NULL, NULL, NULL, 1, 0, 1),
    (13, '用户新增', 'system:user:add',    11, 3, NULL, NULL, NULL, 2, 0, 1),
    (14, '用户编辑', 'system:user:edit',   11, 3, NULL, NULL, NULL, 3, 0, 1),
    (15, '用户删除', 'system:user:delete', 11, 3, NULL, NULL, NULL, 4, 0, 1),
    (16, '重置密码', 'system:user:resetPwd', 11, 3, NULL, NULL, NULL, 5, 0, 1),

    -- 角色管理 + 按钮
    (20, '角色管理', 'system:role',     10, 2, '/system/role',  'system/role/index',      'UserRound',     2, 1, 1),
    (21, '角色查询', 'system:role:query',   20, 3, NULL, NULL, NULL, 1, 0, 1),
    (22, '角色新增', 'system:role:add',     20, 3, NULL, NULL, NULL, 2, 0, 1),
    (23, '角色编辑', 'system:role:edit',    20, 3, NULL, NULL, NULL, 3, 0, 1),
    (24, '角色删除', 'system:role:delete',  20, 3, NULL, NULL, NULL, 4, 0, 1),
    (25, '分配权限', 'system:role:assign',  20, 3, NULL, NULL, NULL, 5, 0, 1),

    -- 菜单管理 + 按钮
    (30, '菜单管理', 'system:menu',     10, 2, '/system/menu',  'system/menu/index',      'Menu',           3, 1, 1),
    (31, '菜单查询', 'system:menu:query',   30, 3, NULL, NULL, NULL, 1, 0, 1),
    (32, '菜单新增', 'system:menu:add',     30, 3, NULL, NULL, NULL, 2, 0, 1),
    (33, '菜单编辑', 'system:menu:edit',    30, 3, NULL, NULL, NULL, 3, 0, 1),
    (34, '菜单删除', 'system:menu:delete',  30, 3, NULL, NULL, NULL, 4, 0, 1),

    -- 部门管理（第二阶段实现页面，当前不显示）
    (40, '部门管理', 'system:dept',     10, 2, '/system/dept',  'system/dept/index',      'Building2', 4, 0, 1),
    (41, '部门查询', 'system:dept:query',   40, 3, NULL, NULL, NULL, 1, 0, 1),
    (42, '部门新增', 'system:dept:add',     40, 3, NULL, NULL, NULL, 2, 0, 1),
    (43, '部门编辑', 'system:dept:edit',    40, 3, NULL, NULL, NULL, 3, 0, 1),
    (44, '部门删除', 'system:dept:delete',  40, 3, NULL, NULL, NULL, 4, 0, 1);

-- ----------------------------------------------------------
-- 7. 角色-菜单关联
--    超级管理员：全部菜单；其余角色：仅工作台（后续阶段按权限矩阵调整）
-- ----------------------------------------------------------
INSERT INTO `oa_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `oa_menu` WHERE `id` BETWEEN 1 AND 100;

INSERT INTO `oa_role_menu` (`role_id`, `menu_id`) VALUES
    (2, 1), (3, 1), (4, 1), (5, 1);

-- ----------------------------------------------------------
-- 验证语句（执行后可手动跑一遍核对）
-- ----------------------------------------------------------
-- SELECT (SELECT COUNT(*) FROM oa_company)   AS company,
--        (SELECT COUNT(*) FROM oa_department) AS dept,
--        (SELECT COUNT(*) FROM oa_role)       AS role,
--        (SELECT COUNT(*) FROM oa_user)       AS user,
--        (SELECT COUNT(*) FROM oa_menu)       AS menu,
--        (SELECT COUNT(*) FROM oa_user_role)  AS user_role,
--        (SELECT COUNT(*) FROM oa_role_menu)  AS role_menu;
