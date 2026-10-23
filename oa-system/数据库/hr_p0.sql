-- =============================================================
-- 人力资源模块 P0 建表/改造脚本
-- 目标：岗位与员工档案（P0-1）+ 考勤基础（P0-2）数据结构
-- 执行方式（必须 stdin + utf8mb4，否则中文注释与菜单名会被写坏）：
--   mysql -uroot -p --default-character-set=utf8mb4 oa_system < hr_p0.sql
-- 幂等：全部使用 IF NOT EXISTS / 先判断再 ALTER，可重复执行
-- =============================================================

-- -------------------------------------------------------------
-- 1. 岗位
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `oa_position` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `position_code` varchar(50)     NOT NULL                COMMENT '岗位编码（同公司内唯一）',
  `position_name` varchar(100)    NOT NULL                COMMENT '岗位名称',
  `company_id`    bigint unsigned NOT NULL                COMMENT '所属公司ID',
  `dept_id`       bigint unsigned NOT NULL                COMMENT '所属部门ID',
  `series`        varchar(8)      NOT NULL DEFAULT 'P'    COMMENT '序列：M管理 P技术 S职能 O操作',
  `level_code`    varchar(16)              DEFAULT NULL   COMMENT '职级，如 P5/M2',
  `headcount`     int             NOT NULL DEFAULT 0      COMMENT '编制数，0=未设置',
  `description`   varchar(500)             DEFAULT NULL   COMMENT '岗位说明',
  `status`        tinyint         NOT NULL DEFAULT 1      COMMENT '状态：0停用 1启用',
  `sort`          int                      DEFAULT 0      COMMENT '排序号',
  `create_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    tinyint         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  `active_flag`   tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL
                  COMMENT '仅供唯一索引：存活=1，已删=NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_code` (`company_id`, `position_code`, `active_flag`),
  KEY `idx_position_dept` (`dept_id`),
  KEY `idx_position_name` (`position_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '岗位表';

-- -------------------------------------------------------------
-- 2. 编制（部门 × 岗位 × 年度）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `oa_position_headcount` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '编制ID',
  `dept_id`     bigint unsigned NOT NULL                COMMENT '部门ID',
  `position_id` bigint unsigned NOT NULL                COMMENT '岗位ID',
  `year`        int             NOT NULL                COMMENT '年度',
  `headcount`   int             NOT NULL DEFAULT 0      COMMENT '编制数',
  `remark`      varchar(255)             DEFAULT NULL   COMMENT '调整说明',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  tinyint         NOT NULL DEFAULT 0,
  `active_flag` tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_headcount` (`dept_id`, `position_id`, `year`, `active_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '岗位编制表';

-- -------------------------------------------------------------
-- 3. 劳动合同 / 证照 / 银行社保 / 异动记录
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `oa_employee_contract` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '合同ID',
  `user_id`       bigint unsigned NOT NULL                COMMENT '员工ID',
  `contract_no`   varchar(64)     NOT NULL                COMMENT '合同编号',
  `contract_type` tinyint         NOT NULL DEFAULT 1      COMMENT '类型：1固定期限 2无固定期限 3实习 4劳务 5返聘',
  `start_date`    date            NOT NULL                COMMENT '开始日期',
  `end_date`      date                     DEFAULT NULL   COMMENT '结束日期（无固定期限为 NULL）',
  `sign_count`    int             NOT NULL DEFAULT 1      COMMENT '签订次数',
  `sign_date`     date                     DEFAULT NULL   COMMENT '签订日期',
  `file_ids`      varchar(255)             DEFAULT NULL   COMMENT '扫描件 file_id，逗号分隔',
  `status`        tinyint         NOT NULL DEFAULT 1      COMMENT '状态：1生效 2已到期 3已解除',
  `remark`        varchar(255)             DEFAULT NULL,
  `create_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    tinyint         NOT NULL DEFAULT 0,
  `active_flag`   tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`, `active_flag`),
  KEY `idx_contract_user` (`user_id`),
  KEY `idx_contract_end` (`end_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '劳动合同表';

CREATE TABLE IF NOT EXISTS `oa_employee_cert` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '证照ID',
  `user_id`     bigint unsigned NOT NULL                COMMENT '员工ID',
  `cert_type`   tinyint         NOT NULL                COMMENT '类型：1学历 2学位 3职业资格 4职称 5特种作业 6培训证书',
  `cert_name`   varchar(100)    NOT NULL                COMMENT '证照名称',
  `cert_no`     varchar(64)              DEFAULT NULL   COMMENT '证书编号',
  `issue_org`   varchar(100)             DEFAULT NULL   COMMENT '发证机构',
  `issue_date`  date                     DEFAULT NULL   COMMENT '发证日期',
  `expire_date` date                     DEFAULT NULL   COMMENT '有效期至（NULL=长期）',
  `file_ids`    varchar(255)             DEFAULT NULL   COMMENT '扫描件 file_id',
  `remark`      varchar(255)             DEFAULT NULL,
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  tinyint         NOT NULL DEFAULT 0,
  `active_flag` tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  KEY `idx_cert_user` (`user_id`),
  KEY `idx_cert_expire` (`expire_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工证照表';

CREATE TABLE IF NOT EXISTS `oa_employee_bank` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '账号ID',
  `user_id`      bigint unsigned NOT NULL                COMMENT '员工ID',
  `bank_name`    varchar(100)             DEFAULT NULL   COMMENT '开户行',
  `bank_account` varchar(255)             DEFAULT NULL   COMMENT '银行卡号（加密）',
  `social_no`    varchar(255)             DEFAULT NULL   COMMENT '社保号（加密）',
  `fund_no`      varchar(255)             DEFAULT NULL   COMMENT '公积金账号（加密）',
  `create_time`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   tinyint         NOT NULL DEFAULT 0,
  `active_flag`  tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_user` (`user_id`, `active_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工银行与社保账号';

CREATE TABLE IF NOT EXISTS `oa_employee_change` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '异动ID',
  `user_id`       bigint unsigned NOT NULL                COMMENT '员工ID',
  `change_type`   tinyint         NOT NULL                COMMENT '类型：1入职 2转正 3调岗 4调薪 5离职 6退休',
  `effective_date` date           NOT NULL                COMMENT '生效日期',
  `before_json`   varchar(1000)            DEFAULT NULL   COMMENT '变更前快照',
  `after_json`    varchar(1000)            DEFAULT NULL   COMMENT '变更后快照',
  `instance_id`   bigint unsigned          DEFAULT NULL   COMMENT '关联流程实例ID',
  `remark`        varchar(255)             DEFAULT NULL   COMMENT '变更说明',
  `operator_id`   bigint unsigned          DEFAULT NULL   COMMENT '操作人ID',
  `create_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    tinyint         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_change_user` (`user_id`, `effective_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工异动记录';

-- -------------------------------------------------------------
-- 4. 员工档案扩展字段（oa_user）
-- -------------------------------------------------------------
ALTER TABLE `oa_user`
  ADD COLUMN `employment_status` tinyint NOT NULL DEFAULT 3 COMMENT '任职状态：1待入职 2试用 3正式 4待离职 5已离职 6退休' AFTER `status`,
  ADD COLUMN `position_id`        bigint unsigned DEFAULT NULL COMMENT '岗位ID（oa_position）' AFTER `position`,
  ADD COLUMN `direct_leader_id`   bigint unsigned DEFAULT NULL COMMENT '直属上级用户ID' AFTER `position_id`,
  ADD COLUMN `work_start_date`    date DEFAULT NULL COMMENT '首次参加工作时间（年假计算）' AFTER `entry_date`,
  ADD COLUMN `probation_end_date` date DEFAULT NULL COMMENT '试用期截止日' AFTER `work_start_date`,
  ADD COLUMN `id_card`            varchar(255) DEFAULT NULL COMMENT '身份证号（加密/脱敏）' AFTER `mobile`,
  ADD COLUMN `birth_date`         date DEFAULT NULL COMMENT '出生日期' AFTER `id_card`,
  ADD COLUMN `marital_status`     tinyint DEFAULT NULL COMMENT '婚姻状况：1未婚 2已婚 3离异 4丧偶' AFTER `birth_date`,
  ADD COLUMN `native_place`       varchar(100) DEFAULT NULL COMMENT '户籍地' AFTER `marital_status`,
  ADD COLUMN `address`            varchar(255) DEFAULT NULL COMMENT '现居住地址' AFTER `native_place`,
  ADD COLUMN `emergency_name`     varchar(50) DEFAULT NULL COMMENT '紧急联系人' AFTER `address`,
  ADD COLUMN `emergency_phone`    varchar(20) DEFAULT NULL COMMENT '紧急联系人电话' AFTER `emergency_name`,
  ADD KEY `idx_user_employment` (`employment_status`),
  ADD KEY `idx_user_position` (`position_id`);

-- -------------------------------------------------------------
-- 5. 班次 / 节假日 / 排班 / 假期额度
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `oa_shift` (
  `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '班次ID',
  `shift_code`     varchar(32)     NOT NULL                COMMENT '班次编码',
  `shift_name`     varchar(64)     NOT NULL                COMMENT '班次名称',
  `work_start`     time            NOT NULL                COMMENT '上班时间',
  `work_end`       time            NOT NULL                COMMENT '下班时间（小于上班时间表示跨天）',
  `rest_minutes`   int             NOT NULL DEFAULT 60     COMMENT '休息时长（分钟）',
  `late_grace`     int             NOT NULL DEFAULT 5      COMMENT '迟到宽限（分钟）',
  `early_grace`    int             NOT NULL DEFAULT 0      COMMENT '早退宽限（分钟）',
  `flexible`       tinyint         NOT NULL DEFAULT 0      COMMENT '是否弹性：0否 1是（不判迟到早退）',
  `free_clock`     tinyint         NOT NULL DEFAULT 0      COMMENT '是否免打卡：0否 1是',
  `status`         tinyint         NOT NULL DEFAULT 1      COMMENT '状态：0停用 1启用',
  `create_time`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     tinyint         NOT NULL DEFAULT 0,
  `active_flag`    tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shift_code` (`shift_code`, `active_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班次表';

CREATE TABLE IF NOT EXISTS `oa_holiday` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '节假日ID',
  `holiday_date` date            NOT NULL                COMMENT '日期',
  `day_type`     tinyint         NOT NULL                COMMENT '类型：2休息日 3法定节假日 4调休上班',
  `holiday_name` varchar(64)              DEFAULT NULL   COMMENT '名称',
  `create_time`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   tinyint         NOT NULL DEFAULT 0,
  `active_flag`  tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_holiday_date` (`holiday_date`, `active_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '节假日与调休日';

CREATE TABLE IF NOT EXISTS `oa_shift_schedule` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '排班ID',
  `user_id`       bigint unsigned NOT NULL                COMMENT '员工ID',
  `schedule_date` date            NOT NULL                COMMENT '排班日期',
  `shift_id`      bigint unsigned          DEFAULT NULL   COMMENT '班次ID，NULL=休息/免打卡',
  `day_type`      tinyint         NOT NULL DEFAULT 1      COMMENT '日期类型：1工作日 2休息日 3法定节假日 4调休上班',
  `settled`       tinyint         NOT NULL DEFAULT 0      COMMENT '是否已日结：0否 1是',
  `create_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    tinyint         NOT NULL DEFAULT 0,
  `active_flag`   tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_user_date` (`user_id`, `schedule_date`, `active_flag`),
  KEY `idx_schedule_date` (`schedule_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '排班表';

CREATE TABLE IF NOT EXISTS `oa_leave_quota` (
  `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '额度ID',
  `user_id`         bigint unsigned NOT NULL                COMMENT '员工ID',
  `leave_type`      tinyint         NOT NULL                COMMENT '假别：1事假 2病假 3年假 4婚假 5产假 6陪产假 7丧假 8调休',
  `year`            int             NOT NULL                COMMENT '年度',
  `total_days`      decimal(5,1)    NOT NULL DEFAULT 0      COMMENT '年度总额度',
  `carry_over_days` decimal(5,1)    NOT NULL DEFAULT 0      COMMENT '上年结转',
  `used_days`       decimal(5,1)    NOT NULL DEFAULT 0      COMMENT '已使用',
  `locked_days`     decimal(5,1)    NOT NULL DEFAULT 0      COMMENT '审批中占用',
  `expire_date`     date                     DEFAULT NULL   COMMENT '结转额度失效日',
  `remark`          varchar(255)             DEFAULT NULL,
  `create_time`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      tinyint         NOT NULL DEFAULT 0,
  `active_flag`     tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quota_user_type_year` (`user_id`, `leave_type`, `year`, `active_flag`),
  KEY `idx_quota_year` (`year`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '假期额度台账';

-- -------------------------------------------------------------
-- 6. 月度考勤汇总
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `oa_attendance_month` (
  `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '汇总ID',
  `user_id`         bigint unsigned NOT NULL                COMMENT '员工ID',
  `month`           char(7)         NOT NULL                COMMENT '月份 yyyy-MM',
  `should_days`     decimal(4,1)    NOT NULL DEFAULT 0      COMMENT '应出勤天数',
  `actual_days`     decimal(4,1)    NOT NULL DEFAULT 0      COMMENT '实际出勤天数',
  `late_count`      int             NOT NULL DEFAULT 0      COMMENT '迟到次数',
  `early_count`     int             NOT NULL DEFAULT 0      COMMENT '早退次数',
  `absent_days`     decimal(4,1)    NOT NULL DEFAULT 0      COMMENT '旷工天数',
  `missing_count`   int             NOT NULL DEFAULT 0      COMMENT '缺卡次数',
  `leave_days_json` json                     DEFAULT NULL   COMMENT '各假别天数',
  `overtime_json`   json                     DEFAULT NULL   COMMENT '各类加班小时',
  `trip_days`       decimal(4,1)    NOT NULL DEFAULT 0      COMMENT '出差天数',
  `status`          tinyint         NOT NULL DEFAULT 1      COMMENT '状态：1未封账 2已封账',
  `lock_time`       datetime                 DEFAULT NULL   COMMENT '封账时间',
  `lock_by`         bigint unsigned          DEFAULT NULL   COMMENT '封账人',
  `create_time`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      tinyint         NOT NULL DEFAULT 0,
  `active_flag`     tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_att_month_user` (`user_id`, `month`, `active_flag`),
  KEY `idx_att_month` (`month`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '月度考勤汇总';

-- -------------------------------------------------------------
-- 7. 改造 oa_leave / oa_attendance
-- -------------------------------------------------------------
ALTER TABLE `oa_leave`
  ADD COLUMN `bill_code`          varchar(64) DEFAULT NULL COMMENT '单据编号' AFTER `id`,
  ADD COLUMN `actual_days`        decimal(4,1) DEFAULT NULL COMMENT '销假后实际休假天数' AFTER `leave_days`,
  ADD COLUMN `return_date`        date DEFAULT NULL COMMENT '实际返岗日期' AFTER `actual_days`,
  ADD COLUMN `cancel_status`      tinyint NOT NULL DEFAULT 0 COMMENT '销假状态：0未销假 1销假审批中 2已销假' AFTER `leave_status`,
  ADD COLUMN `cancel_instance_id` bigint unsigned DEFAULT NULL COMMENT '销假流程实例ID' AFTER `cancel_status`,
  ADD COLUMN `attachment_ids`     varchar(255) DEFAULT NULL COMMENT '证明材料 file_id' AFTER `reason`,
  ADD COLUMN `active_flag`        tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  ADD UNIQUE KEY `uk_leave_bill` (`bill_code`, `active_flag`);

-- oa_attendance 原有 uk_user_date(user_id, attendance_date) 不含 active_flag：
-- 逻辑删后同一天无法重建记录，这里改为三列唯一键。
ALTER TABLE `oa_attendance`
  DROP INDEX `uk_user_date`,
  ADD COLUMN `card_missing`     tinyint NOT NULL DEFAULT 0 COMMENT '缺卡标记：0无 1缺上班卡 2缺下班卡 3缺两次',
  ADD COLUMN `check_in_source`  tinyint DEFAULT NULL COMMENT '上班打卡来源：1系统 2考勤机 3补卡',
  ADD COLUMN `check_out_source` tinyint DEFAULT NULL COMMENT '下班打卡来源：1系统 2考勤机 3补卡',
  ADD COLUMN `shift_id`         bigint unsigned DEFAULT NULL COMMENT '班次ID',
  ADD COLUMN `settled`          tinyint NOT NULL DEFAULT 0 COMMENT '是否已日结：0否 1是',
  ADD COLUMN `is_deleted`       tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  ADD COLUMN `active_flag`      tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  ADD UNIQUE KEY `uk_att_user_date` (`user_id`, `attendance_date`, `active_flag`);

-- -------------------------------------------------------------
-- 8. 字典（人力资源）
-- -------------------------------------------------------------
INSERT INTO `oa_dict_type` (`dict_type`, `dict_name`, `status`, `remark`)
SELECT * FROM (SELECT 'hr_employment_status' AS a, '任职状态' AS b, 1 AS c, '人力资源模块' AS d) t
WHERE NOT EXISTS (SELECT 1 FROM `oa_dict_type` WHERE `dict_type` = 'hr_employment_status' AND `is_deleted` = 0);

INSERT INTO `oa_dict_type` (`dict_type`, `dict_name`, `status`, `remark`)
SELECT * FROM (SELECT 'hr_position_series' AS a, '岗位序列' AS b, 1 AS c, '人力资源模块' AS d) t
WHERE NOT EXISTS (SELECT 1 FROM `oa_dict_type` WHERE `dict_type` = 'hr_position_series' AND `is_deleted` = 0);

INSERT INTO `oa_dict_type` (`dict_type`, `dict_name`, `status`, `remark`)
SELECT * FROM (SELECT 'hr_contract_type' AS a, '合同类型' AS b, 1 AS c, '人力资源模块' AS d) t
WHERE NOT EXISTS (SELECT 1 FROM `oa_dict_type` WHERE `dict_type` = 'hr_contract_type' AND `is_deleted` = 0);

INSERT INTO `oa_dict_type` (`dict_type`, `dict_name`, `status`, `remark`)
SELECT * FROM (SELECT 'hr_cert_type' AS a, '证照类型' AS b, 1 AS c, '人力资源模块' AS d) t
WHERE NOT EXISTS (SELECT 1 FROM `oa_dict_type` WHERE `dict_type` = 'hr_cert_type' AND `is_deleted` = 0);

INSERT INTO `oa_dict_type` (`dict_type`, `dict_name`, `status`, `remark`)
SELECT * FROM (SELECT 'hr_leave_type' AS a, '假别' AS b, 1 AS c, '人力资源模块' AS d) t
WHERE NOT EXISTS (SELECT 1 FROM `oa_dict_type` WHERE `dict_type` = 'hr_leave_type' AND `is_deleted` = 0);

INSERT INTO `oa_dict_type` (`dict_type`, `dict_name`, `status`, `remark`)
SELECT * FROM (SELECT 'hr_attendance_status' AS a, '考勤状态' AS b, 1 AS c, '人力资源模块' AS d) t
WHERE NOT EXISTS (SELECT 1 FROM `oa_dict_type` WHERE `dict_type` = 'hr_attendance_status' AND `is_deleted` = 0);

INSERT INTO `oa_dict_data` (`dict_type`, `item_label`, `item_value`, `sort`, `status`) VALUES
('hr_employment_status', '待入职', '1', 1, 1),
('hr_employment_status', '试用',   '2', 2, 1),
('hr_employment_status', '正式',   '3', 3, 1),
('hr_employment_status', '待离职', '4', 4, 1),
('hr_employment_status', '已离职', '5', 5, 1),
('hr_employment_status', '退休',   '6', 6, 1),
('hr_position_series', '管理序列', 'M', 1, 1),
('hr_position_series', '技术序列', 'P', 2, 1),
('hr_position_series', '职能序列', 'S', 3, 1),
('hr_position_series', '操作序列', 'O', 4, 1),
('hr_contract_type', '固定期限',   '1', 1, 1),
('hr_contract_type', '无固定期限', '2', 2, 1),
('hr_contract_type', '实习',       '3', 3, 1),
('hr_contract_type', '劳务',       '4', 4, 1),
('hr_contract_type', '返聘',       '5', 5, 1),
('hr_cert_type', '学历',       '1', 1, 1),
('hr_cert_type', '学位',       '2', 2, 1),
('hr_cert_type', '职业资格',   '3', 3, 1),
('hr_cert_type', '职称',       '4', 4, 1),
('hr_cert_type', '特种作业',   '5', 5, 1),
('hr_cert_type', '培训证书',   '6', 6, 1),
('hr_leave_type', '事假',   '1', 1, 1),
('hr_leave_type', '病假',   '2', 2, 1),
('hr_leave_type', '年假',   '3', 3, 1),
('hr_leave_type', '婚假',   '4', 4, 1),
('hr_leave_type', '产假',   '5', 5, 1),
('hr_leave_type', '陪产假', '6', 6, 1),
('hr_leave_type', '丧假',   '7', 7, 1),
('hr_leave_type', '调休',   '8', 8, 1),
('hr_attendance_status', '正常', '1', 1, 1),
('hr_attendance_status', '迟到', '2', 2, 1),
('hr_attendance_status', '早退', '3', 3, 1),
('hr_attendance_status', '缺勤', '4', 4, 1),
('hr_attendance_status', '假勤', '5', 5, 1);

-- -------------------------------------------------------------
-- 9. 菜单与授权（P0：目录 + 5 个页面 + 按钮）
-- -------------------------------------------------------------
INSERT INTO `oa_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `menu_code`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT * FROM (SELECT 230 AS a, 0 AS b, '人力资源' AS c, 1 AS d, '/hr' AS e, 'hr' AS f, NULL AS g, 'Users' AS h, 8 AS i, 1 AS j, 1 AS k) t
WHERE NOT EXISTS (SELECT 1 FROM `oa_menu` WHERE `id` = 230);

INSERT INTO `oa_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `menu_code`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(231, 230, '员工档案', 2, '/hr/employee',      'hr:employee',      'hr/employee/index',      'IdCard',        1, 1, 1),
(232, 230, '岗位管理', 2, '/hr/position',      'hr:position',      'hr/position/index',      'Briefcase',     2, 1, 1),
(233, 230, '考勤管理', 2, '/hr/attendance',    'hr:attendance',    'hr/attendance/index',    'CalendarCheck', 3, 1, 1),
(234, 230, '假勤管理', 2, '/hr/leave',         'hr:leave',         'hr/leave/index',         'CalendarDays',  4, 1, 1),
(235, 230, '我的考勤', 2, '/hr/my-attendance', 'hr:my-attendance', 'hr/my-attendance/index', 'Clock',         5, 1, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);

INSERT INTO `oa_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `menu_code`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(236, 231, '新增员工', 3, NULL, 'hr:employee:create',      NULL, NULL, 1, 1, 1),
(237, 231, '编辑档案', 3, NULL, 'hr:employee:update',      NULL, NULL, 2, 1, 1),
(238, 231, '导入',     3, NULL, 'hr:employee:import',      NULL, NULL, 3, 1, 1),
(239, 231, '导出',     3, NULL, 'hr:employee:export',      NULL, NULL, 4, 1, 1),
(240, 232, '新增岗位', 3, NULL, 'hr:position:create',      NULL, NULL, 1, 1, 1),
(241, 232, '编辑岗位', 3, NULL, 'hr:position:update',      NULL, NULL, 2, 1, 1),
(242, 232, '删除岗位', 3, NULL, 'hr:position:delete',      NULL, NULL, 3, 1, 1),
(243, 233, '重算考勤', 3, NULL, 'hr:attendance:rebuild',   NULL, NULL, 1, 1, 1),
(244, 233, '封账',     3, NULL, 'hr:attendance:lock',      NULL, NULL, 2, 1, 1),
(245, 233, '导入考勤', 3, NULL, 'hr:attendance:import',    NULL, NULL, 3, 1, 1),
(246, 233, '班次维护', 3, NULL, 'hr:attendance:shift',     NULL, NULL, 4, 1, 1),
(247, 233, '排班',     3, NULL, 'hr:attendance:schedule',  NULL, NULL, 5, 1, 1),
(248, 234, '调整额度', 3, NULL, 'hr:leave:quota',          NULL, NULL, 1, 1, 1),
(249, 234, '导出',     3, NULL, 'hr:leave:export',         NULL, NULL, 2, 1, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);

-- 授权：超管 / 人事专员 / 人事经理
INSERT IGNORE INTO `oa_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
  FROM `oa_role` r
  JOIN `oa_menu` m ON m.`id` BETWEEN 230 AND 249
 WHERE r.`role_code` IN ('SUPER_ADMIN', 'HR', 'HR_MANAGER')
   AND r.`is_deleted` = 0;

-- 我的考勤：全体员工可见（EMPLOYEE 角色）
INSERT IGNORE INTO `oa_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, 235 FROM `oa_role` r WHERE r.`role_code` = 'EMPLOYEE' AND r.`is_deleted` = 0;
