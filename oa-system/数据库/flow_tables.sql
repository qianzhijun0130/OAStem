-- 第三阶段：流程引擎业务表（与 Flowable 自动生成的 ACT_ 表同库共存）
-- 注意：JSON 列在实体中按 String 映射（存 JSON 文本），由 Java 侧序列化/反序列化。
USE oa_system;

-- 流程模板定义表：元数据 + 关联的 Flowable 部署信息
CREATE TABLE IF NOT EXISTS oa_flow_definition (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  flow_code     VARCHAR(64)  NOT NULL COMMENT '流程编码（如 leave/overtime），全局唯一',
  flow_name     VARCHAR(100) NOT NULL COMMENT '流程名称',
  category      VARCHAR(32)  DEFAULT NULL COMMENT '分类',
  bpmn_key      VARCHAR(100) DEFAULT NULL COMMENT 'Flowable 流程定义 key（对应 BPMN process id）',
  deployment_id VARCHAR(100) DEFAULT NULL COMMENT 'Flowable 部署ID',
  form_fields   JSON         DEFAULT NULL COMMENT '表单字段配置 JSON',
  node_config   JSON         DEFAULT NULL COMMENT '节点审批人规则 JSON',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark        VARCHAR(255) DEFAULT NULL,
  create_time   DATETIME     DEFAULT NULL,
  update_time   DATETIME     DEFAULT NULL,
  is_deleted    TINYINT      NOT NULL DEFAULT 0,
  -- 仅供唯一索引使用：存活=1、已删=NULL（MySQL 唯一索引允许多个 NULL），
  -- 使 flow_code 在软删除后即可复用，且业务值不被改写
  active_flag   TINYINT      GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_code (flow_code, active_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程模板定义表';

-- 审批单实例表：每条申请一行
CREATE TABLE IF NOT EXISTS oa_approval_instance (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  flow_def_id  BIGINT       DEFAULT NULL COMMENT '关联流程模板',
  flow_code    VARCHAR(64)  DEFAULT NULL COMMENT '流程编码',
  title        VARCHAR(200) DEFAULT NULL COMMENT '申请标题',
  applicant_id BIGINT       NOT NULL COMMENT '发起人',
  proc_inst_id VARCHAR(100) DEFAULT NULL COMMENT 'Flowable 流程实例ID',
  form_data    JSON         DEFAULT NULL COMMENT '表单数据 JSON',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1草稿 2审批中 3已通过 4已驳回 5已撤销',
  current_node VARCHAR(100) DEFAULT NULL COMMENT '当前节点',
  create_time  DATETIME     DEFAULT NULL,
  update_time  DATETIME     DEFAULT NULL,
  is_deleted   TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_applicant (applicant_id),
  KEY idx_proc_inst (proc_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批单实例表';

-- 审批操作记录表：每次动作一行，同时驱动「已办」与详情时间轴
CREATE TABLE IF NOT EXISTS oa_approval_record (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  instance_id BIGINT      NOT NULL COMMENT '关联审批单',
  task_id    VARCHAR(100) DEFAULT NULL COMMENT 'Flowable 任务ID',
  node_id    VARCHAR(100) DEFAULT NULL COMMENT '节点ID',
  node_name  VARCHAR(100) DEFAULT NULL COMMENT '节点名称',
  handler_id BIGINT       DEFAULT NULL COMMENT '操作人',
  action     VARCHAR(20)  DEFAULT NULL COMMENT 'submit/approve/reject/transfer/withdraw',
  opinion    VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  is_deleted  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_instance (instance_id),
  KEY idx_handler (handler_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批操作记录表';
