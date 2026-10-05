-- 第四阶段：系统字典表（数据字典类型 + 字典数据项）
-- 用途：把性别、公告类型、公告级别、流程分类等枚举从代码里搬到数据库，
--       后续新增一种审批类型/公告级别只需在界面上加一条数据，不必改代码重新发版。
USE oa_system;
SET NAMES utf8mb4;

-- 字典类型表
-- 唯一索引含生成列 active_flag：存活行 active_flag=1、已删行 active_flag=NULL，
-- 利用「MySQL 唯一索引允许多个 NULL」——删除后同一编码可立即重建，且业务值不被改写。
-- 该手法是 MySQL 下替代 PostgreSQL partial unique index（WHERE is_deleted = 0）的通行做法，
-- 详见 开发文档/数据库/逻辑删除唯一索引企业级改造方案.md
CREATE TABLE IF NOT EXISTS oa_dict_type (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  dict_name   VARCHAR(100) NOT NULL COMMENT '字典名称，如「性别」',
  dict_type   VARCHAR(100) NOT NULL COMMENT '字典类型编码，如 sys_gender',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark      VARCHAR(255) DEFAULT NULL,
  -- 与项目其它表保持一致：建表即给默认值，避免脚本 INSERT 未显式传值时落入 NULL
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted  TINYINT      NOT NULL DEFAULT 0,
  active_flag TINYINT      GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL
                           COMMENT '仅供唯一索引使用：存活=1，已删=NULL',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_type (dict_type, active_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典类型表';

-- 字典数据项表
-- 不建「类型 + 值」唯一索引：逻辑删除下该索引会阻止「删掉某一项后重新加回同值」，
-- 而改写字典值又会破坏历史业务数据对它的引用。重复校验交给业务层，索引只保证查询性能。
CREATE TABLE IF NOT EXISTS oa_dict_data (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  dict_type   VARCHAR(100) NOT NULL COMMENT '所属字典类型编码',
  item_label  VARCHAR(100) NOT NULL COMMENT '显示名称',
  item_value  VARCHAR(100) NOT NULL COMMENT '数据值',
  sort        INT          NOT NULL DEFAULT 0 COMMENT '排序，升序',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  css_class   VARCHAR(50)  DEFAULT NULL COMMENT '前端标签样式，如 el-tag 的 success/warning/danger',
  is_default  TINYINT      NOT NULL DEFAULT 0 COMMENT '1表示该类型的默认选中项',
  remark      VARCHAR(255) DEFAULT NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_dict_type (dict_type),
  KEY idx_type_status (dict_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典项表';

-- ============ 初始化：先接入的 4 类枚举 ============

INSERT INTO oa_dict_type (dict_name, dict_type, status, remark) VALUES
  ('性别',     'sys_gender',    1, '用户性别选项'),
  ('公告类型', 'notice_type',   1, '公告通知的分类'),
  ('公告级别', 'notice_level',  1, '公告紧急程度，影响前端标签颜色'),
  ('流程分类', 'flow_category', 1, '审批流程模板的分类')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), status = VALUES(status), remark = VALUES(remark);

-- 以下插入均带「该类型下尚无数据」守卫，脚本可安全重复执行，不会产生重复字典项。
-- 性别：与 oa_user.gender 的 0/1/2 约定一致
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_default)
SELECT * FROM (
  SELECT 'sys_gender' AS a, '未知' AS b, '0' AS c, 1 AS d, 1 AS e, 1 AS f UNION ALL
  SELECT 'sys_gender', '男', '1', 2, 1, 0 UNION ALL
  SELECT 'sys_gender', '女', '2', 3, 1, 0
) AS t
WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'sys_gender' AND is_deleted = 0);

-- 公告类型：与 oa_notice.notice_type 约定一致
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, css_class)
SELECT * FROM (
  SELECT 'notice_type' AS a, '通知' AS b, '1' AS c, 1 AS d, 1 AS e, 'info' AS f UNION ALL
  SELECT 'notice_type', '公告', '2', 2, 1, 'primary' UNION ALL
  SELECT 'notice_type', '制度', '3', 3, 1, 'warning'
) AS t
WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'notice_type' AND is_deleted = 0);

-- 公告级别：与 oa_notice.notice_level 约定一致
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, css_class)
SELECT * FROM (
  SELECT 'notice_level' AS a, '普通' AS b, '1' AS c, 1 AS d, 1 AS e, 'info' AS f UNION ALL
  SELECT 'notice_level', '重要', '2', 2, 1, 'warning' UNION ALL
  SELECT 'notice_level', '紧急', '3', 3, 1, 'danger'
) AS t
WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'notice_level' AND is_deleted = 0);

-- 流程分类：item_value 直接用中文，是为了兼容 oa_flow_definition.category 已有的存量值
-- （内置的 leave/expense 模板分类为「内置流程」），避免接入字典时还要回头改业务数据。
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status)
SELECT * FROM (
  SELECT 'flow_category' AS a, '内置流程' AS b, '内置流程' AS c, 1 AS d, 1 AS e UNION ALL
  SELECT 'flow_category', '人事', '人事', 2, 1 UNION ALL
  SELECT 'flow_category', '行政', '行政', 3, 1 UNION ALL
  SELECT 'flow_category', '财务', '财务', 4, 1 UNION ALL
  SELECT 'flow_category', '采购', '采购', 5, 1
) AS t
WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'flow_category' AND is_deleted = 0);

SELECT dt.dict_type, dt.dict_name, COUNT(dd.id) AS item_count
FROM oa_dict_type dt LEFT JOIN oa_dict_data dd ON dd.dict_type = dt.dict_type AND dd.is_deleted = 0
WHERE dt.is_deleted = 0
GROUP BY dt.dict_type, dt.dict_name ORDER BY dt.id;
