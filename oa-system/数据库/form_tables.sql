-- FormCreate 低代码表单：表单定义表 + 流程节点绑定表
-- 设计原则：
--   1. 表单定义独立成表（oa_form），流程节点通过关联表（oa_flow_node_form）绑定，
--      不侵入 oa_flow_definition.node_config —— 节点审批人规则与表单生命周期不同，混写会互相污染。
--   2. rule_json / field_schema 用 JSON 列（实体侧按 String 映射），与 flow_tables.sql 保持一致。
--   3. 唯一索引统一带 active_flag 生成列（存活=1、已删=NULL），实现「软删除后编码立即可复用且业务值不被改写」。
-- 注意：执行前请确认 MySQL 版本 >= 8.0.13（生成列 + 唯一索引多 NULL 语义）。
USE oa_system;

-- 表单定义表：一条记录 = 一个可复用的表单模板
CREATE TABLE IF NOT EXISTS oa_form (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  form_code    VARCHAR(64)  NOT NULL COMMENT '表单编码（如 leave_apply），全局唯一',
  form_name    VARCHAR(100) NOT NULL COMMENT '表单名称',
  category     VARCHAR(32)  DEFAULT NULL COMMENT '分类',
  -- FormCreate 渲染器规则 JSON：[{"type":"input","prop":"days","label":"请假天数","rule":{"required":true}}]
  -- ⚠️ type 必须是 FormCreate 内置类型名，不能用 Element Plus 组件短名，否则控件静默不渲染（页面空白且无报错）。
  --    正确：input / inputNumber / select / radio / checkbox / switch / slider / upload
  --          / datePicker / timePicker / rate / cascader / tree / colorPicker / divider
  --    错误：number（应 inputNumber）、date（应 datePicker）、time（应 timePicker）
  --    多行文本：{"type":"input","props":{"type":"textarea"}}
  rule_json    LONGTEXT     NOT NULL COMMENT 'FormCreate 规则 JSON（option 数组）',
  -- 字段清单 JSON：[{"prop":"days","label":"请假天数","type":"inputNumber","required":true}]，
  -- 由后端从 rule_json 派生，供发起页做必填预校验、详情页回显表头，后端据此复核提交数据
  field_schema LONGTEXT     DEFAULT NULL COMMENT '字段清单 JSON（从 rule_json 派生）',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark       VARCHAR(255) DEFAULT NULL,
  create_by    BIGINT       DEFAULT NULL,
  create_time  DATETIME     DEFAULT NULL,
  update_by    BIGINT       DEFAULT NULL,
  update_time  DATETIME     DEFAULT NULL,
  is_deleted   TINYINT      NOT NULL DEFAULT 0,
  active_flag  TINYINT      GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_form_code (form_code, active_flag),
  KEY idx_form_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单定义表';

-- 流程节点↔表单绑定表：一个节点最多绑一个表单，同一表单可被多个流程/节点复用
CREATE TABLE IF NOT EXISTS oa_flow_node_form (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  flow_def_id BIGINT       NOT NULL COMMENT '关联 oa_flow_definition.id',
  node_key    VARCHAR(100) NOT NULL COMMENT 'BPMN taskDefinitionKey（如 deptLeaderAudit）',
  node_name   VARCHAR(100) DEFAULT NULL COMMENT '节点名称（冗余，便于列表展示）',
  form_id     BIGINT       NOT NULL COMMENT '关联 oa_form.id',
  -- 1 = 发起环节主表单（发起页据此渲染「申请内容」区块）；0 = 中间/末端节点表单（当前仅登记不渲染）
  is_primary  TINYINT      NOT NULL DEFAULT 1 COMMENT '1发起环节主表单 0非主表单',
  sort        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  create_by   BIGINT       DEFAULT NULL,
  create_time DATETIME     DEFAULT NULL,
  update_by   BIGINT       DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  is_deleted  TINYINT      NOT NULL DEFAULT 0,
  active_flag TINYINT      GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (id),
  -- 同一流程同一节点只能有一条存活绑定；删除后（active_flag 变 NULL）可重新绑定
  UNIQUE KEY uk_flow_node (flow_def_id, node_key, active_flag),
  KEY idx_form (form_id),
  KEY idx_flow_def (flow_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点表单绑定表';