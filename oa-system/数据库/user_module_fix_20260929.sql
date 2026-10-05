-- =============================================================
-- 用户管理模块缺陷修复（2026-09-29）
-- 对应需求文档：开发文档/用户管理/2026-09-29-01.md
--
-- 变更内容：
--   1) oa_department 增加「工号前缀」与「工号流水号」两列，支撑工号按部门自动分配
--   2) oa_user.gender 默认值由 0(未知) 改为 1(男)，并同步列注释
--   3) oa_menu 增加「导入用户」按钮权限码 system:user:import
--   4) oa_dict_data 性别字典下线「未知」项，默认值改挂「男」
--
-- 幂等性说明：使用 information_schema 判断后再变更，可重复执行。
-- 注意：id 不可随手写死，本脚本新增菜单不指定 id，由自增分配。
-- =============================================================
USE oa_system;
SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1) 部门表：工号前缀 + 工号流水号
-- -------------------------------------------------------------
-- work_no_prefix：部门名称汉字拼音首字母（全大写），如「技术部」→ JSB。
--   由后端在首次取号时自动推导并回写，管理员可在部门管理界面手工覆盖。
-- work_no_seq：该部门已发出的最大流水号。只增不减——员工离职/删除后不回退，
--   以此保证工号「不回收、不复用」。
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE oa_department ADD COLUMN work_no_prefix VARCHAR(16) NULL COMMENT ''工号前缀（部门名称拼音首字母，大写）'' AFTER dept_code',
    'SELECT ''oa_department.work_no_prefix 已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'oa_system' AND TABLE_NAME = 'oa_department' AND COLUMN_NAME = 'work_no_prefix');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE oa_department ADD COLUMN work_no_seq INT NOT NULL DEFAULT 0 COMMENT ''工号流水号当前值（只增不减，保证工号不复用）'' AFTER work_no_prefix',
    'SELECT ''oa_department.work_no_seq 已存在，跳过''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'oa_system' AND TABLE_NAME = 'oa_department' AND COLUMN_NAME = 'work_no_seq');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2) 用户表：性别默认值 0(未知) → 1(男)
-- -------------------------------------------------------------
-- 业务上已取消「未知」，保留 0 会造成新建用户在选择器里「无任何选中项」。
ALTER TABLE oa_user
    MODIFY COLUMN gender tinyint DEFAULT 1 COMMENT '性别：1男 2女';

-- 存量兜底：历史数据里若存在 0/NULL，统一刷成「男」，避免列表出现空白性别
UPDATE oa_user SET gender = 1 WHERE gender IS NULL OR gender = 0;

-- -------------------------------------------------------------
-- 3) 「导入用户」按钮权限
-- -------------------------------------------------------------
-- 父级：用户管理(id=11)。按钮 visible=0，不进侧边栏。
-- 权限码必须与 UserController 的 @PreAuthorize 完全一致。
INSERT INTO oa_menu (menu_name, menu_code, parent_id, menu_type, sort, visible, status)
VALUES ('导入用户', 'system:user:import', 11, 3, 6, 0, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name),
                        menu_code = VALUES(menu_code),
                        parent_id = VALUES(parent_id),
                        menu_type = VALUES(menu_type),
                        sort = VALUES(sort),
                        visible = VALUES(visible),
                        status = VALUES(status);

-- 授权：仅超级管理员（role_id=1）。其它角色如需导入，另行授权。
INSERT IGNORE INTO oa_role_menu (role_id, menu_id)
SELECT 1, m.id FROM oa_menu m WHERE m.menu_code = 'system:user:import';

-- -------------------------------------------------------------
-- 4) 性别字典：下线「未知」
-- -------------------------------------------------------------
-- 逻辑删除而非物理删除，保留字典变更痕迹（列表接口只查 is_deleted = 0）。
UPDATE oa_dict_data SET is_deleted = 1
WHERE dict_type = 'sys_gender' AND item_value = '0' AND is_deleted = 0;

-- 默认值改挂「男」
UPDATE oa_dict_data SET is_default = 1
WHERE dict_type = 'sys_gender' AND item_value = '1' AND is_deleted = 0;

-- -------------------------------------------------------------
-- 校验输出
-- -------------------------------------------------------------
SELECT id, dept_name, dept_code, work_no_prefix, work_no_seq FROM oa_department ORDER BY id;
SELECT id, menu_name, menu_code, parent_id, menu_type FROM oa_menu WHERE menu_code LIKE 'system:user%' ORDER BY id;
SELECT id, item_label, item_value, is_default, is_deleted FROM oa_dict_data WHERE dict_type = 'sys_gender' ORDER BY id;
