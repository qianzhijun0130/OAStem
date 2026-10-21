-- 流程节点表单字段权限表
-- 设计原则：
--   1. 粒度 = (流程模板, 节点, 表单字段)。一个节点对一份表单的不同字段可以有不同读写权限，
--      例如销假节点上「销假日期」可编辑，而「请假天数」仍只读。
--   2. 与 oa_flow_node_form（节点↔表单绑定）职责分离：那张表回答「这个节点用哪份表单」，
--      本表回答「这份表单在该节点上每个字段能看/能改到什么程度」。
--   3. 未配置记录 = 默认只读。理由：审批场景下「默认放开」风险高，
--      审批人误改申请人填的内容比看不到更难追责。
-- 注意：执行前请确认 MySQL 版本 >= 8.0.13（生成列 + 唯一索引多 NULL 语义）。
USE oa_system;

CREATE TABLE IF NOT EXISTS oa_flow_node_form_field (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  -- 关联 oa_flow_definition.id
  flow_def_id BIGINT       NOT NULL COMMENT '流程模板 id',
  -- BPMN taskDefinitionKey；发起环节统一用 init。刻意不用节点名称，改名不应丢配置
  node_key    VARCHAR(100) NOT NULL COMMENT '节点标识（BPMN taskDefinitionKey，发起环节为 init）',
  node_name   VARCHAR(100)          DEFAULT NULL COMMENT '节点名称（冗余，便于列表展示）',
  -- 关联 oa_form.id。冗余存一份，便于按表单反查哪些节点对它做了特殊授权
  form_id     BIGINT       NOT NULL COMMENT '表单 id（冗余 oa_flow_node_form.form_id）',
  -- FormCreate 规则里的 prop
  prop        VARCHAR(100) NOT NULL COMMENT '字段标识（FormCreate rule 的 prop）',
  field_label VARCHAR(100)          DEFAULT NULL COMMENT '字段显示名（冗余，便于列表展示）',
  -- 1只读 2可编辑 3隐藏。缺省 1
  permission  TINYINT      NOT NULL DEFAULT 1 COMMENT '权限：1只读 2可编辑 3隐藏',

  create_by   BIGINT                DEFAULT NULL COMMENT '创建人',
  update_by   BIGINT                DEFAULT NULL COMMENT '更新人',
  create_time DATETIME              DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME              DEFAULT NULL COMMENT '更新时间',
  is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  -- 存活=1、已删=NULL，配合唯一索引实现「软删除后 (流程,节点,字段) 可立即重配」
  active_flag TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL COMMENT '唯一索引辅助列',

  PRIMARY KEY (id),
  -- 同一 (流程, 节点, 字段) 只允许一条存活配置
  UNIQUE KEY uk_flow_node_field (flow_def_id, node_key, prop, active_flag),
  -- 按表单反查：哪些节点对它做了非默认权限
  KEY idx_form_id (form_id),
  -- 按节点查配置：渲染时按 (流程, 节点) 一次性取全量
  KEY idx_flow_node (flow_def_id, node_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='流程节点表单字段权限';
