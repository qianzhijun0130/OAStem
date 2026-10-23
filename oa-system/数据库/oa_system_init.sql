-- =============================================================
-- OA 企业办公管理系统 - 数据库初始化脚本
-- 适用环境：MySQL 8.0+ / InnoDB / utf8mb4_unicode_ci
-- 生成时间：2026-09-28
-- 说明：按模块顺序建表，先组织架构、权限，再业务模块
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS oa_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE oa_system;

-- =============================================================
-- 一、组织架构模块
-- =============================================================

-- 公司表
CREATE TABLE IF NOT EXISTS `oa_company` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公司ID',
  `company_name` VARCHAR(100) NOT NULL COMMENT '公司名称',
  `company_code` VARCHAR(50) NOT NULL COMMENT '公司编码',
  `parent_id` BIGINT UNSIGNED DEFAULT 0 COMMENT '上级公司ID，0为顶级',
  `legal_person` VARCHAR(50) DEFAULT NULL COMMENT '法人代表',
  `address` VARCHAR(255) DEFAULT NULL COMMENT '公司地址',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '公司邮箱',
  `sort` INT DEFAULT 0 COMMENT '排序号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  `active_flag` TINYINT GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL COMMENT '仅供唯一索引使用：存活=1，已删=NULL（MySQL 唯一索引允许多个 NULL）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_code` (`company_code`, `active_flag`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司表';

-- 部门表
CREATE TABLE IF NOT EXISTS `oa_department` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `dept_name` VARCHAR(100) NOT NULL COMMENT '部门名称',
  `dept_code` VARCHAR(50) NOT NULL COMMENT '部门编码',
  `company_id` BIGINT UNSIGNED NOT NULL COMMENT '所属公司ID',
  `parent_id` BIGINT UNSIGNED DEFAULT 0 COMMENT '上级部门ID',
  `leader_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '部门负责人ID',
  `dept_type` TINYINT DEFAULT 1 COMMENT '部门类型',
  `sort` INT DEFAULT 0 COMMENT '排序号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `active_flag` TINYINT GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL COMMENT '仅供唯一索引使用：存活=1，已删=NULL（MySQL 唯一索引允许多个 NULL）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`, `active_flag`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- 用户表
CREATE TABLE IF NOT EXISTS `oa_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
  `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `gender` TINYINT DEFAULT 0 COMMENT '性别：0未知 1男 2女',
  `mobile` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `company_id` BIGINT UNSIGNED NOT NULL COMMENT '所属公司ID',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '所属部门ID',
  `position` VARCHAR(50) DEFAULT NULL COMMENT '职位',
  `entry_date` DATE DEFAULT NULL COMMENT '入职日期',
  `work_number` VARCHAR(50) DEFAULT NULL COMMENT '工号',
  `sort` INT DEFAULT 0 COMMENT '排序号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用 2锁定',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  `active_flag` TINYINT GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL COMMENT '仅供唯一索引使用：存活=1，已删=NULL（MySQL 唯一索引允许多个 NULL）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`, `active_flag`),
  KEY `idx_mobile` (`mobile`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =============================================================
-- 二、权限管理模块
-- =============================================================

-- 角色表
CREATE TABLE IF NOT EXISTS `oa_role` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `role_type` TINYINT NOT NULL DEFAULT 1 COMMENT '角色类型：1系统角色 2业务角色',
  `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据权限范围',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
  `sort` INT DEFAULT 0 COMMENT '排序号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `active_flag` TINYINT GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL COMMENT '仅供唯一索引使用：存活=1，已删=NULL（MySQL 唯一索引允许多个 NULL）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `oa_user_role` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 菜单表
CREATE TABLE IF NOT EXISTS `oa_menu` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` VARCHAR(50) NOT NULL COMMENT '菜单名称',
  `menu_code` VARCHAR(100) NOT NULL COMMENT '菜单权限编码',
  `parent_id` BIGINT UNSIGNED DEFAULT 0 COMMENT '父菜单ID',
  `menu_type` TINYINT NOT NULL COMMENT '类型：1目录 2菜单 3按钮',
  `path` VARCHAR(200) DEFAULT NULL COMMENT '路由路径',
  `component` VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
  `icon` VARCHAR(50) DEFAULT NULL COMMENT '菜单图标',
  `sort` INT DEFAULT 0 COMMENT '排序号',
  `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_menu_code` (`menu_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单表';

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS `oa_role_menu` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '菜单ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

-- =============================================================
-- 三、流程审批模块
-- =============================================================

-- 流程定义表
CREATE TABLE IF NOT EXISTS `oa_process_definition` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程ID',
  `process_name` VARCHAR(100) NOT NULL COMMENT '流程名称',
  `process_key` VARCHAR(50) NOT NULL COMMENT '流程标识',
  `process_category` VARCHAR(50) DEFAULT NULL COMMENT '流程分类',
  `form_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联表单ID',
  `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
  `xml_content` TEXT COMMENT '流程XML定义',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '流程描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0草稿 1发布 2停用',
  `create_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_process_key` (`process_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义表';

-- 流程实例表
CREATE TABLE IF NOT EXISTS `oa_process_instance` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '实例ID',
  `process_def_id` BIGINT UNSIGNED NOT NULL COMMENT '流程定义ID',
  `business_key` VARCHAR(100) NOT NULL COMMENT '业务主键',
  `business_type` VARCHAR(50) NOT NULL COMMENT '业务类型',
  `title` VARCHAR(200) NOT NULL COMMENT '流程标题',
  `start_user_id` BIGINT UNSIGNED NOT NULL COMMENT '发起人ID',
  `start_dept_id` BIGINT UNSIGNED NOT NULL COMMENT '发起部门ID',
  `current_node` VARCHAR(50) NOT NULL COMMENT '当前节点标识',
  `current_node_name` VARCHAR(50) NOT NULL COMMENT '当前节点名称',
  `instance_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1审批中 2已通过 3已驳回 4已撤销',
  `priority` TINYINT DEFAULT 2 COMMENT '优先级：1低 2中 3高',
  `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_process_def_id` (`process_def_id`),
  KEY `idx_start_user_id` (`start_user_id`),
  KEY `idx_instance_status` (`instance_status`),
  KEY `idx_business` (`business_key`,`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例表';

-- 审批任务表
CREATE TABLE IF NOT EXISTS `oa_process_task` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  `node_key` VARCHAR(50) NOT NULL COMMENT '节点标识',
  `node_name` VARCHAR(50) NOT NULL COMMENT '节点名称',
  `task_type` TINYINT NOT NULL DEFAULT 1 COMMENT '任务类型：1审批 2抄送',
  `assignee_id` BIGINT UNSIGNED NOT NULL COMMENT '办理人ID',
  `candidate_ids` VARCHAR(500) DEFAULT NULL COMMENT '候选人ID列表',
  `task_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1待办 2已办 3转交 4撤回',
  `approval_opinion` TEXT COMMENT '审批意见',
  `approval_result` TINYINT DEFAULT NULL COMMENT '审批结果：1同意 2驳回 3退回',
  `handle_time` DATETIME DEFAULT NULL COMMENT '办理时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_assignee_id` (`assignee_id`),
  KEY `idx_task_status` (`task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批任务表';

-- 审批流转记录表
CREATE TABLE IF NOT EXISTS `oa_process_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  `task_id` BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  `node_key` VARCHAR(50) NOT NULL COMMENT '节点标识',
  `node_name` VARCHAR(50) NOT NULL COMMENT '节点名称',
  `handle_user_id` BIGINT UNSIGNED NOT NULL COMMENT '处理人ID',
  `handle_user_name` VARCHAR(50) NOT NULL COMMENT '处理人姓名',
  `action_type` TINYINT NOT NULL COMMENT '操作类型',
  `opinion` TEXT COMMENT '处理意见',
  `handle_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流转记录表';

-- =============================================================
-- 四、公文管理模块
-- =============================================================

-- 公文表
CREATE TABLE IF NOT EXISTS `oa_document` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公文ID',
  `doc_title` VARCHAR(200) NOT NULL COMMENT '公文标题',
  `doc_number` VARCHAR(50) DEFAULT NULL COMMENT '公文编号',
  `doc_type` TINYINT NOT NULL COMMENT '公文类型：1通知 2报告 3请示 4批复',
  `doc_level` TINYINT NOT NULL DEFAULT 2 COMMENT '紧急程度：1平件 2加急 3特急',
  `secret_level` TINYINT NOT NULL DEFAULT 0 COMMENT '密级：0普通 1秘密 2机密 3绝密',
  `main_send_dept` VARCHAR(500) DEFAULT NULL COMMENT '主送部门',
  `copy_send_dept` VARCHAR(500) DEFAULT NULL COMMENT '抄送部门',
  `content` LONGTEXT COMMENT '公文正文',
  `attachment_ids` VARCHAR(500) DEFAULT NULL COMMENT '附件ID列表',
  `instance_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联流程实例ID',
  `doc_status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1审批中 2已发布 3已归档',
  `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
  `create_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人',
  `create_dept` BIGINT UNSIGNED NOT NULL COMMENT '创建部门',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_doc_status` (`doc_status`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公文表';

-- =============================================================
-- 五、会议管理模块
-- =============================================================

-- 会议室表
CREATE TABLE IF NOT EXISTS `oa_meeting_room` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议室ID',
  `room_name` VARCHAR(100) NOT NULL COMMENT '会议室名称',
  `room_code` VARCHAR(50) NOT NULL COMMENT '会议室编码',
  `capacity` INT NOT NULL COMMENT '容纳人数',
  `location` VARCHAR(200) DEFAULT NULL COMMENT '位置',
  `equipment` VARCHAR(500) DEFAULT NULL COMMENT '设备配置',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用 1可用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `active_flag` TINYINT GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL COMMENT '仅供唯一索引使用：存活=1，已删=NULL（MySQL 唯一索引允许多个 NULL）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_code` (`room_code`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室表';

-- 会议表
CREATE TABLE IF NOT EXISTS `oa_meeting` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议ID',
  `meeting_title` VARCHAR(200) NOT NULL COMMENT '会议标题',
  `meeting_type` TINYINT NOT NULL DEFAULT 1 COMMENT '会议类型',
  `room_id` BIGINT UNSIGNED NOT NULL COMMENT '会议室ID',
  `organizer_id` BIGINT UNSIGNED NOT NULL COMMENT '组织者ID',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `attendee_ids` TEXT COMMENT '参会人ID列表',
  `attendee_names` TEXT COMMENT '参会人姓名列表',
  `meeting_summary` TEXT COMMENT '会议纪要',
  `attachment_ids` VARCHAR(500) DEFAULT NULL COMMENT '附件ID列表',
  `meeting_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1预约 2进行中 3已结束 4已取消',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_room_id` (`room_id`),
  KEY `idx_organizer_id` (`organizer_id`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议表';

-- =============================================================
-- 六、任务管理模块
-- =============================================================

-- 任务表
CREATE TABLE IF NOT EXISTS `oa_task` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_title` VARCHAR(200) NOT NULL COMMENT '任务标题',
  `task_content` TEXT COMMENT '任务内容',
  `task_type` TINYINT NOT NULL DEFAULT 1 COMMENT '任务类型',
  `priority` TINYINT NOT NULL DEFAULT 2 COMMENT '优先级：1低 2中 3高',
  `task_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1待处理 2进行中 3已完成 4已延期',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '完成进度(0-100)',
  `assigner_id` BIGINT UNSIGNED NOT NULL COMMENT '指派人ID',
  `executor_id` BIGINT UNSIGNED NOT NULL COMMENT '执行人ID',
  `participant_ids` TEXT COMMENT '参与人ID列表',
  `parent_id` BIGINT UNSIGNED DEFAULT 0 COMMENT '父任务ID',
  `start_time` DATETIME DEFAULT NULL COMMENT '计划开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '计划结束时间',
  `actual_start_time` DATETIME DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` DATETIME DEFAULT NULL COMMENT '实际结束时间',
  `attachment_ids` VARCHAR(500) DEFAULT NULL COMMENT '附件ID列表',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_executor_id` (`executor_id`),
  KEY `idx_task_status` (`task_status`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- 任务评论表
CREATE TABLE IF NOT EXISTS `oa_task_comment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `task_id` BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '评论人ID',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `attachment_ids` VARCHAR(500) DEFAULT NULL COMMENT '附件ID列表',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务评论表';

-- =============================================================
-- 七、通知公告模块
-- =============================================================

-- 公告表
CREATE TABLE IF NOT EXISTS `oa_notice` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `notice_type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1通知 2公告 3新闻',
  `notice_level` TINYINT NOT NULL DEFAULT 2 COMMENT '级别：1普通 2重要 3紧急',
  `content` LONGTEXT COMMENT '公告内容',
  `attachment_ids` VARCHAR(500) DEFAULT NULL COMMENT '附件ID列表',
  `publish_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '发布范围：1全部 2指定部门 3指定人员',
  `scope_dept_ids` TEXT COMMENT '指定部门ID列表',
  `scope_user_ids` TEXT COMMENT '指定人员ID列表',
  `notice_status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已发布 2已下架',
  `scheduled_publish_time` DATETIME DEFAULT NULL COMMENT '计划发布时间；到点由定时任务自动发布，为空表示不启用定时发布',
  `expire_time` DATETIME DEFAULT NULL COMMENT '自动下架时间；到点由定时任务自动下架，为空表示永不下架',
  `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
  `publisher_id` BIGINT UNSIGNED NOT NULL COMMENT '发布人ID',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '查看次数',
  `top_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0否 1是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_notice_status` (`notice_status`),
  KEY `idx_scheduled_publish` (`scheduled_publish_time`),
  KEY `idx_expire_time` (`expire_time`),
  KEY `idx_publish_time` (`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- 公告阅读记录表
CREATE TABLE IF NOT EXISTS `oa_notice_read` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `notice_id` BIGINT UNSIGNED NOT NULL COMMENT '公告ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告阅读记录表';

-- =============================================================
-- 八、日程管理模块
-- =============================================================

-- 日程表
CREATE TABLE IF NOT EXISTS `oa_schedule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日程ID',
  `schedule_title` VARCHAR(200) NOT NULL COMMENT '日程标题',
  `schedule_type` TINYINT NOT NULL DEFAULT 1 COMMENT '日程类型',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `all_day` TINYINT NOT NULL DEFAULT 0 COMMENT '是否全天：0否 1是',
  `location` VARCHAR(200) DEFAULT NULL COMMENT '地点',
  `description` TEXT COMMENT '描述',
  `reminder` TINYINT NOT NULL DEFAULT 0 COMMENT '提醒方式',
  `reminder_time` DATETIME DEFAULT NULL COMMENT '提醒时间',
  `repeat_type` TINYINT NOT NULL DEFAULT 0 COMMENT '重复类型：0不重复 1每天 2每周 3每月',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日程表';

-- =============================================================
-- 九、文档管理模块
-- =============================================================

-- 文件夹表
CREATE TABLE IF NOT EXISTS `oa_file_folder` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件夹ID',
  `folder_name` VARCHAR(100) NOT NULL COMMENT '文件夹名称',
  `parent_id` BIGINT UNSIGNED DEFAULT 0 COMMENT '父文件夹ID',
  `folder_type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1个人 2公共',
  `create_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件夹表';

-- 文件表（file_path 存相对路径，如 2026/09/28/abc123.pdf）
CREATE TABLE IF NOT EXISTS `oa_file` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件原名',
  `file_path` VARCHAR(500) NOT NULL COMMENT '文件存储路径',
  `file_size` BIGINT NOT NULL COMMENT '文件大小（字节）',
  `file_type` VARCHAR(50) NOT NULL COMMENT '文件类型',
  `file_suffix` VARCHAR(20) DEFAULT NULL COMMENT '文件后缀',
  `md5` VARCHAR(32) DEFAULT NULL COMMENT '文件MD5值',
  `folder_id` BIGINT UNSIGNED DEFAULT 0 COMMENT '所属文件夹ID',
  `uploader_id` BIGINT UNSIGNED NOT NULL COMMENT '上传人ID',
  `download_count` INT NOT NULL DEFAULT 0 COMMENT '下载次数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_folder_id` (`folder_id`),
  KEY `idx_uploader_id` (`uploader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';

-- =============================================================
-- 十、考勤管理模块
-- =============================================================

-- 考勤记录表
CREATE TABLE IF NOT EXISTS `oa_attendance` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `attendance_date` DATE NOT NULL COMMENT '考勤日期',
  `check_in_time` DATETIME DEFAULT NULL COMMENT '签到时间',
  `check_out_time` DATETIME DEFAULT NULL COMMENT '签退时间',
  `check_in_location` VARCHAR(200) DEFAULT NULL COMMENT '签到地点',
  `check_out_location` VARCHAR(200) DEFAULT NULL COMMENT '签退地点',
  `work_duration` DECIMAL(4,2) DEFAULT NULL COMMENT '工作时长（小时）',
  `attendance_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常 2迟到 3早退 4缺勤 5请假',
  `late_minutes` INT DEFAULT 0 COMMENT '迟到分钟数',
  `early_minutes` INT DEFAULT 0 COMMENT '早退分钟数',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`attendance_date`),
  KEY `idx_attendance_date` (`attendance_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考勤记录表';

-- 请假申请表
CREATE TABLE IF NOT EXISTS `oa_leave` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '请假ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '申请人ID',
  `leave_type` TINYINT NOT NULL COMMENT '请假类型：1事假 2病假 3年假 4婚假 5产假',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `leave_days` DECIMAL(4,1) NOT NULL COMMENT '请假天数',
  `reason` TEXT NOT NULL COMMENT '请假原因',
  `instance_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联流程实例ID',
  `leave_status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1审批中 2已通过 3已驳回',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_leave_status` (`leave_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请假申请表';

-- =============================================================
-- 十一、系统日志模块
-- =============================================================

-- 操作日志表
CREATE TABLE IF NOT EXISTS `oa_operation_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作用户ID',
  `username` VARCHAR(50) DEFAULT NULL COMMENT '操作账号',
  `module` VARCHAR(50) DEFAULT NULL COMMENT '模块名称',
  `operation` VARCHAR(100) DEFAULT NULL COMMENT '操作描述',
  `method` VARCHAR(200) DEFAULT NULL COMMENT '请求方法',
  `request_params` TEXT COMMENT '请求参数',
  `response_result` TEXT COMMENT '响应结果',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
  `location` VARCHAR(255) DEFAULT NULL COMMENT '操作地点',
  `cost_time` INT DEFAULT NULL COMMENT '耗时（毫秒）',
  `operation_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1成功 0失败',
  `error_msg` TEXT COMMENT '错误信息',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 操作日志归档表：clean 清理前先把数据搬入此表（主表为物理删除，无归档即永久丢失）
-- 主键沿用原日志 id 以便溯源；写入侧使用 INSERT IGNORE 使重复归档幂等
CREATE TABLE IF NOT EXISTS `oa_operation_log_archive` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '沿用原操作日志ID，便于溯源',
  `user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作用户ID',
  `username` VARCHAR(50) DEFAULT NULL COMMENT '操作账号',
  `module` VARCHAR(50) DEFAULT NULL COMMENT '模块名称',
  `operation` VARCHAR(100) DEFAULT NULL COMMENT '操作描述',
  `method` VARCHAR(200) DEFAULT NULL COMMENT '请求方法',
  `request_params` TEXT COMMENT '请求参数',
  `response_result` TEXT COMMENT '响应结果',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
  `location` VARCHAR(255) DEFAULT NULL COMMENT '操作地点',
  `cost_time` INT DEFAULT NULL COMMENT '耗时（毫秒）',
  `operation_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1成功 0失败',
  `error_msg` TEXT COMMENT '错误信息',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `archived_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_archive_create_time` (`create_time`),
  KEY `idx_archive_archived_time` (`archived_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志归档表';

-- 登录日志表
CREATE TABLE IF NOT EXISTS `oa_login_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '用户ID',
  `username` VARCHAR(50) DEFAULT NULL COMMENT '登录账号',
  `login_type` TINYINT NOT NULL DEFAULT 1 COMMENT '登录类型：1账号密码 2手机验证码',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT '登录IP',
  `location` VARCHAR(255) DEFAULT NULL COMMENT '登录地点',
  `browser` VARCHAR(50) DEFAULT NULL COMMENT '浏览器',
  `os` VARCHAR(50) DEFAULT NULL COMMENT '操作系统',
  `login_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1成功 0失败',
  `fail_reason` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- 登录日志归档表：与 oa_operation_log_archive 同一口径（clean 前先搬入，主键沿用原 id）
CREATE TABLE IF NOT EXISTS `oa_login_log_archive` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '沿用原登录日志ID，便于溯源',
  `user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '用户ID',
  `username` VARCHAR(50) DEFAULT NULL COMMENT '登录账号',
  `login_type` TINYINT NOT NULL DEFAULT 1 COMMENT '登录类型：1账号密码 2手机验证码',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT '登录IP',
  `location` VARCHAR(255) DEFAULT NULL COMMENT '登录地点',
  `browser` VARCHAR(50) DEFAULT NULL COMMENT '浏览器',
  `os` VARCHAR(50) DEFAULT NULL COMMENT '操作系统',
  `login_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1成功 0失败',
  `fail_reason` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `archived_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_archive_create_time` (`create_time`),
  KEY `idx_archive_archived_time` (`archived_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志归档表';

SET FOREIGN_KEY_CHECKS = 1;

-- 完成
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'oa_system'
ORDER BY TABLE_NAME;
