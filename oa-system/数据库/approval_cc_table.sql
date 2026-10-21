-- ============================================================
-- 审批抄送表（oa_approval_cc）
-- 归属功能：任务中心 → 抄送任务
-- 执行时机：首次部署时，在 oa_system_init.sql 之后执行
-- 说明：
--   1) 逻辑删除沿用项目约定：唯一索引带 active_flag 生成列，
--      已删行 active_flag 为 NULL，唯一约束自然让位，删后可重新抄送同一人；
--   2) 生成列不能引自增列，故 active_flag 只依赖 is_deleted。
-- ============================================================

CREATE TABLE IF NOT EXISTS `oa_approval_cc` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `instance_id` BIGINT NOT NULL COMMENT '审批单ID',
  `user_id` BIGINT NOT NULL COMMENT '被抄送人',
  `cc_by` BIGINT DEFAULT NULL COMMENT '执行抄送操作的人',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '抄送说明',
  `read_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `active_flag` TINYINT GENERATED ALWAYS AS (IF(`is_deleted` = 0, 1, NULL)) VIRTUAL COMMENT '逻辑删唯一索引辅助列',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cc` (`instance_id`, `user_id`, `active_flag`),
  KEY `idx_cc_user` (`user_id`, `read_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批抄送';
