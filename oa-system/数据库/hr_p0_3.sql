-- =============================================================
-- 人力资源 P0-3 假勤：加班 / 出差 / 补卡 / 地方假别政策
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 oa_system < hr_p0_3.sql
-- 幂等：全部 IF NOT EXISTS / 先判断再插
-- =============================================================

-- 地方假别政策（婚假/产假/陪产假按城市配置，不写死常量）
CREATE TABLE IF NOT EXISTS `oa_leave_policy` (
  `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '政策ID',
  `city`           varchar(32)     NOT NULL                COMMENT '城市',
  `leave_type`     tinyint         NOT NULL                COMMENT '假别：4婚假 5产假 6陪产假 7丧假',
  `days`           decimal(4,1)    NOT NULL                COMMENT '法定天数',
  `effective_year` int             NOT NULL                COMMENT '生效年度',
  `remark`         varchar(255)             DEFAULT NULL   COMMENT '依据（如：XX省人口与计划生育条例）',
  `create_time`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     tinyint         NOT NULL DEFAULT 0,
  `active_flag`    tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_policy_city_type_year` (`city`, `leave_type`, `effective_year`, `active_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '地方假别政策';

-- 加班单
CREATE TABLE IF NOT EXISTS `oa_overtime` (
  `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '加班ID',
  `bill_code`        varchar(64)              DEFAULT NULL   COMMENT '单据编号',
  `user_id`          bigint unsigned NOT NULL                COMMENT '申请人ID',
  `overtime_date`    date            NOT NULL                COMMENT '加班日期',
  `overtime_type`    tinyint         NOT NULL DEFAULT 1      COMMENT '类型：1工作日 2休息日 3法定节假日',
  `start_time`       datetime                 DEFAULT NULL   COMMENT '开始时间',
  `end_time`         datetime                 DEFAULT NULL   COMMENT '结束时间',
  `hours`            decimal(4,1)    NOT NULL DEFAULT 0      COMMENT '加班时长（小时）',
  `compensate_type`  tinyint         NOT NULL DEFAULT 1      COMMENT '补偿：1转调休 2加班费',
  `reason`           varchar(500)             DEFAULT NULL   COMMENT '加班事由',
  `instance_id`      bigint unsigned          DEFAULT NULL   COMMENT '关联流程实例ID',
  `overtime_status`  tinyint         NOT NULL DEFAULT 0      COMMENT '状态：0草稿 1审批中 2已通过 3已驳回 4已撤销',
  `create_time`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       tinyint         NOT NULL DEFAULT 0,
  `active_flag`      tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_overtime_bill` (`bill_code`, `active_flag`),
  KEY `idx_overtime_user` (`user_id`, `overtime_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '加班单';

-- 出差单
CREATE TABLE IF NOT EXISTS `oa_business_trip` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '出差ID',
  `bill_code`     varchar(64)              DEFAULT NULL   COMMENT '单据编号',
  `user_id`       bigint unsigned NOT NULL                COMMENT '申请人ID',
  `destination`   varchar(200)    NOT NULL                COMMENT '出差地点',
  `start_date`    date            NOT NULL                COMMENT '开始日期',
  `end_date`      date            NOT NULL                COMMENT '结束日期',
  `days`          decimal(4,1)    NOT NULL DEFAULT 0      COMMENT '出差天数',
  `budget`        decimal(12,2)            DEFAULT NULL   COMMENT '预算金额',
  `reason`        varchar(500)             DEFAULT NULL   COMMENT '出差事由',
  `instance_id`   bigint unsigned          DEFAULT NULL   COMMENT '关联流程实例ID',
  `trip_status`   tinyint         NOT NULL DEFAULT 0      COMMENT '状态：0草稿 1审批中 2已通过 3已驳回 4已撤销',
  `create_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    tinyint         NOT NULL DEFAULT 0,
  `active_flag`   tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trip_bill` (`bill_code`, `active_flag`),
  KEY `idx_trip_user` (`user_id`, `start_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '出差单';

-- 补卡单
CREATE TABLE IF NOT EXISTS `oa_card_supplement` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '补卡ID',
  `bill_code`     varchar(64)              DEFAULT NULL   COMMENT '单据编号',
  `user_id`       bigint unsigned NOT NULL                COMMENT '申请人ID',
  `card_date`     date            NOT NULL                COMMENT '补卡日期',
  `direction`     tinyint         NOT NULL                COMMENT '方向：1上班 2下班',
  `card_time`     datetime        NOT NULL                COMMENT '补卡时间',
  `reason`        varchar(500)             DEFAULT NULL   COMMENT '补卡原因',
  `instance_id`   bigint unsigned          DEFAULT NULL   COMMENT '关联流程实例ID',
  `card_status`   tinyint         NOT NULL DEFAULT 0      COMMENT '状态：0草稿 1审批中 2已通过 3已驳回 4已撤销',
  `create_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    tinyint         NOT NULL DEFAULT 0,
  `active_flag`   tinyint GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_bill` (`bill_code`, `active_flag`),
  UNIQUE KEY `uk_card_user_date_dir` (`user_id`, `card_date`, `direction`, `active_flag`),
  KEY `idx_card_user` (`user_id`, `card_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '补卡单';

-- 默认地方政策（示例城市，可按实际调整；不写死进代码）
INSERT INTO `oa_leave_policy` (`city`, `leave_type`, `days`, `effective_year`, `remark`)
SELECT * FROM (
  SELECT '默认' AS a, 4 AS b, 3.0 AS c, 2026 AS d, '法定婚假 3 天，地方奖励假按城市另配' AS e
) t WHERE NOT EXISTS (SELECT 1 FROM `oa_leave_policy` WHERE `city`='默认' AND `leave_type`=4 AND `effective_year`=2026 AND `is_deleted`=0);

INSERT INTO `oa_leave_policy` (`city`, `leave_type`, `days`, `effective_year`, `remark`)
SELECT * FROM (
  SELECT '默认' AS a, 5 AS b, 98.0 AS c, 2026 AS d, '法定产假 98 天，地方奖励假按城市另配' AS e
) t WHERE NOT EXISTS (SELECT 1 FROM `oa_leave_policy` WHERE `city`='默认' AND `leave_type`=5 AND `effective_year`=2026 AND `is_deleted`=0);

INSERT INTO `oa_leave_policy` (`city`, `leave_type`, `days`, `effective_year`, `remark`)
SELECT * FROM (
  SELECT '默认' AS a, 6 AS b, 15.0 AS c, 2026 AS d, '陪产假默认 15 天，按城市配置' AS e
) t WHERE NOT EXISTS (SELECT 1 FROM `oa_leave_policy` WHERE `city`='默认' AND `leave_type`=6 AND `effective_year`=2026 AND `is_deleted`=0);

-- 假勤管理菜单（P0-3 上线，之前为避免空壳入口已删除）
INSERT INTO `oa_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `menu_code`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT * FROM (SELECT 234 AS a, 230 AS b, '假勤管理' AS c, 2 AS d, '/hr/leave' AS e, 'hr:leave' AS f,
                      'hr/leave/index' AS g, 'CalendarDays' AS h, 4 AS i, 1 AS j, 1 AS k) t
WHERE NOT EXISTS (SELECT 1 FROM `oa_menu` WHERE `id` = 234);

INSERT INTO `oa_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `menu_code`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(248, 234, '调整额度', 3, NULL, 'hr:leave:quota',   NULL, NULL, 1, 1, 1),
(249, 234, '导出',     3, NULL, 'hr:leave:export',  NULL, NULL, 2, 1, 1),
(250, 234, '发起请假', 3, NULL, 'hr:leave:create',  NULL, NULL, 3, 1, 1),
(251, 234, '加班管理', 3, NULL, 'hr:overtime',      NULL, NULL, 4, 1, 1),
(252, 234, '出差管理', 3, NULL, 'hr:trip',          NULL, NULL, 5, 1, 1),
(253, 234, '补卡管理', 3, NULL, 'hr:card',          NULL, NULL, 6, 1, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);

-- 授权：超管 / 人事 / 人事经理 全部；普通员工可见页面（自己发起与查看自己的单据）
INSERT IGNORE INTO `oa_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `oa_role` r JOIN `oa_menu` m ON m.`id` BETWEEN 234 AND 253
WHERE r.`role_code` IN ('SUPER_ADMIN', 'HR', 'HR_MANAGER') AND r.`is_deleted` = 0;

INSERT IGNORE INTO `oa_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, 234 FROM `oa_role` r WHERE r.`role_code` = 'EMPLOYEE' AND r.`is_deleted` = 0;
