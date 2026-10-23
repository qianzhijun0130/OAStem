-- =====================================================================
-- 人力资源 P1-2 绩效：数据模型（2026-10-23）
--   方案 / 等级映射 / 指标库 / 方案指标 / 周期 / 考核表 / 考核表指标 / 校准留痕 / 申诉
-- 口径见 开发文档/人力资源/04_绩效管理.md：
--   · 指标权重合计必须 = 100%，保存时校验
--   · 总分 = Σ(得分 × 权重) + 加分 - 扣分，四舍五入 1 位，封顶 120 / 封底 0
--   · 归档时快照指标名与权重（之后改指标不影响历史解释）
--   · 强制分布默认关闭，开启也只做预警不拦截
-- =====================================================================
USE oa_system;

-- ---------- 1) 绩效方案 ----------
CREATE TABLE IF NOT EXISTS oa_perf_plan (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_code     VARCHAR(50)     NOT NULL COMMENT '方案编码',
    plan_name     VARCHAR(100)    NOT NULL COMMENT '方案名称',
    cycle_type    TINYINT         NOT NULL DEFAULT 2 COMMENT '1月度 2季度 3半年 4年度',
    score_mode    TINYINT         NOT NULL DEFAULT 1 COMMENT '1百分制 2五级制',
    dept_ids      VARCHAR(500)    DEFAULT NULL COMMENT '适用部门（逗号分隔，空=全部）',
    series        VARCHAR(50)     DEFAULT NULL COMMENT '适用岗位序列（空=全部）',
    force_distribution TINYINT    NOT NULL DEFAULT 0 COMMENT '1开启强制分布（仅预警不拦截）',
    distribution_limit DECIMAL(4, 2) NOT NULL DEFAULT 0.20 COMMENT 'A 等级建议占比上限',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1草稿 2启用 3停用',
    remark        VARCHAR(500)    DEFAULT NULL,
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    TINYINT         NOT NULL DEFAULT 0,
    active_flag   TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_code (plan_code, active_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '绩效方案';

-- ---------- 2) 方案等级映射（分数区间 → 等级 → 系数） ----------
CREATE TABLE IF NOT EXISTS oa_perf_plan_grade (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id     BIGINT UNSIGNED NOT NULL,
    grade_code  VARCHAR(20)     NOT NULL COMMENT 'A/B/C/D',
    grade_name  VARCHAR(50)     NOT NULL,
    min_score   DECIMAL(5, 1)   NOT NULL DEFAULT 0,
    max_score   DECIMAL(5, 1)   NOT NULL DEFAULT 100,
    coefficient DECIMAL(4, 2)   NOT NULL DEFAULT 1.00 COMMENT '绩效系数，供薪酬引用',
    sort        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_grade (plan_id, grade_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '方案等级映射';

-- ---------- 3) 指标库 ----------
CREATE TABLE IF NOT EXISTS oa_perf_indicator (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    indicator_code VARCHAR(50)     NOT NULL COMMENT '指标编码',
    indicator_name VARCHAR(100)    NOT NULL COMMENT '指标名称',
    definition     VARCHAR(500)    DEFAULT NULL COMMENT '指标定义 / 计算口径',
    indicator_type TINYINT         NOT NULL DEFAULT 1 COMMENT '1定量 2定性 3加分 4扣分',
    unit           VARCHAR(20)     DEFAULT NULL COMMENT '计量单位',
    score_standard TEXT            DEFAULT NULL COMMENT '五档评分标准描述',
    series         VARCHAR(50)     DEFAULT NULL COMMENT '适用序列',
    status         TINYINT         NOT NULL DEFAULT 1,
    remark         VARCHAR(500)    DEFAULT NULL,
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     TINYINT         NOT NULL DEFAULT 0,
    active_flag    TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_indicator_code (indicator_code, active_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '绩效指标库';

-- ---------- 4) 方案指标（权重在这里） ----------
CREATE TABLE IF NOT EXISTS oa_perf_plan_indicator (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id      BIGINT UNSIGNED NOT NULL,
    indicator_id BIGINT UNSIGNED NOT NULL,
    weight       DECIMAL(5, 2)   NOT NULL DEFAULT 0 COMMENT '权重百分比，同方案合计必须 100',
    sort         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_indicator (plan_id, indicator_id),
    KEY idx_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '方案指标与权重';

-- ---------- 5) 考核周期 ----------
CREATE TABLE IF NOT EXISTS oa_perf_cycle (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cycle_code         VARCHAR(50)     NOT NULL COMMENT '周期编码，如 2026Q4',
    cycle_name         VARCHAR(100)    NOT NULL,
    plan_id            BIGINT UNSIGNED NOT NULL,
    start_date         DATE            NOT NULL,
    end_date           DATE            NOT NULL,
    stage              TINYINT         NOT NULL DEFAULT 1 COMMENT '1目标设定 2自评 3上级评分 4校准 5确认 6归档',
    target_deadline    DATE            DEFAULT NULL,
    self_deadline      DATE            DEFAULT NULL,
    score_deadline     DATE            DEFAULT NULL,
    calibrate_deadline DATE            DEFAULT NULL,
    confirm_deadline   DATE            DEFAULT NULL,
    status             TINYINT         NOT NULL DEFAULT 1 COMMENT '1进行中 2已归档',
    remark             VARCHAR(500)    DEFAULT NULL,
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         TINYINT         NOT NULL DEFAULT 0,
    active_flag        TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cycle_code (cycle_code, active_flag),
    KEY idx_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '绩效考核周期';

-- ---------- 6) 考核表（每人每周期一张） ----------
CREATE TABLE IF NOT EXISTS oa_perf_sheet (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cycle_id         BIGINT UNSIGNED NOT NULL,
    user_id          BIGINT UNSIGNED NOT NULL,
    dept_id          BIGINT UNSIGNED DEFAULT NULL COMMENT '生成时的部门快照',
    leader_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '生成时的直属上级快照',
    plan_id          BIGINT UNSIGNED NOT NULL,
    target_status    TINYINT         NOT NULL DEFAULT 0 COMMENT '0未提交 1待审核 2已锁定 3已驳回',
    self_status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0未提交 1已提交',
    manager_status   TINYINT         NOT NULL DEFAULT 0 COMMENT '0未评分 1已评分',
    calibrate_status TINYINT         NOT NULL DEFAULT 0 COMMENT '0未校准 1已校准',
    confirm_status   TINYINT         NOT NULL DEFAULT 0 COMMENT '0未确认 1已确认',
    total_score      DECIMAL(5, 1)   DEFAULT NULL COMMENT '最终总分（归档口径）',
    self_total       DECIMAL(5, 1)   DEFAULT NULL,
    manager_total    DECIMAL(5, 1)   DEFAULT NULL,
    grade_code       VARCHAR(20)     DEFAULT NULL,
    coefficient      DECIMAL(4, 2)   DEFAULT NULL COMMENT '归档后供薪酬引用；空按 1.0',
    self_missing     TINYINT         NOT NULL DEFAULT 0 COMMENT '1=超期未自评，自动跳过',
    hr_scored        TINYINT         NOT NULL DEFAULT 0 COMMENT '1=HR 代评（上级超期）',
    manager_comment  VARCHAR(1000)   DEFAULT NULL COMMENT '上级总评语',
    remark           VARCHAR(500)    DEFAULT NULL,
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       TINYINT         NOT NULL DEFAULT 0,
    active_flag      TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cycle_user (cycle_id, user_id, active_flag),
    KEY idx_user (user_id),
    KEY idx_leader (leader_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '绩效考核表';

-- ---------- 7) 考核表指标行（快照指标名与权重） ----------
CREATE TABLE IF NOT EXISTS oa_perf_sheet_item (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sheet_id       BIGINT UNSIGNED NOT NULL,
    indicator_id   BIGINT UNSIGNED DEFAULT NULL,
    indicator_code VARCHAR(50)     NOT NULL,
    indicator_name VARCHAR(100)    NOT NULL COMMENT '归档依据：快照名字，后续改指标不影响历史',
    indicator_type TINYINT         NOT NULL DEFAULT 1,
    weight         DECIMAL(5, 2)   NOT NULL DEFAULT 0,
    target_value   VARCHAR(200)    DEFAULT NULL COMMENT '目标值（目标设定阶段填写）',
    measure_standard VARCHAR(500)  DEFAULT NULL COMMENT '衡量标准',
    self_score     DECIMAL(5, 1)   DEFAULT NULL,
    self_comment   VARCHAR(500)    DEFAULT NULL,
    manager_score  DECIMAL(5, 1)   DEFAULT NULL,
    manager_comment VARCHAR(500)   DEFAULT NULL,
    final_score    DECIMAL(5, 1)   DEFAULT NULL COMMENT '校准后的最终分',
    sort           INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sheet (sheet_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考核表指标行';

-- ---------- 8) HR 校准留痕 ----------
CREATE TABLE IF NOT EXISTS oa_perf_calibrate_log (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sheet_id     BIGINT UNSIGNED NOT NULL,
    before_score DECIMAL(5, 1)   DEFAULT NULL,
    after_score  DECIMAL(5, 1)   DEFAULT NULL,
    before_grade VARCHAR(20)     DEFAULT NULL,
    after_grade  VARCHAR(20)     DEFAULT NULL,
    reason       VARCHAR(500)    NOT NULL COMMENT '校准理由（必填）',
    operator_id  BIGINT UNSIGNED DEFAULT NULL,
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sheet (sheet_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '绩效校准留痕';

-- ---------- 9) 申诉（每张考核表只允许一次） ----------
CREATE TABLE IF NOT EXISTS oa_perf_appeal (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sheet_id      BIGINT UNSIGNED NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    reason        VARCHAR(1000)   NOT NULL,
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1待处理 2维持原结果 3调整',
    handle_by     BIGINT UNSIGNED DEFAULT NULL,
    handle_time   DATETIME        DEFAULT NULL,
    handle_opinion VARCHAR(500)   DEFAULT NULL,
    before_score  DECIMAL(5, 1)   DEFAULT NULL,
    after_score   DECIMAL(5, 1)   DEFAULT NULL,
    before_grade  VARCHAR(20)     DEFAULT NULL,
    after_grade   VARCHAR(20)     DEFAULT NULL,
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    TINYINT         NOT NULL DEFAULT 0,
    active_flag   TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sheet_appeal (sheet_id, active_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '绩效申诉';

-- ---------- 10) 菜单与授权 ----------
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 263, 230, '绩效管理', 2, '/hr/performance', 'hr:performance', 'hr/performance/index', 'Target', 8, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 263);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 264, 230, '我的绩效', 2, '/hr/my-performance', 'hr:performance:my', 'hr/my-performance/index', 'TrendingUp', 9, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 264);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 265, 263, '方案维护', 3, NULL, 'hr:performance:plan', NULL, NULL, 1, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 265);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 266, 263, '指标维护', 3, NULL, 'hr:performance:indicator', NULL, NULL, 2, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 266);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 267, 263, '目标审核', 3, NULL, 'hr:performance:target', NULL, NULL, 3, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 267);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 268, 263, '上级评分', 3, NULL, 'hr:performance:score', NULL, NULL, 4, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 268);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 269, 263, '绩效校准', 3, NULL, 'hr:performance:calibrate', NULL, NULL, 5, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 269);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 270, 263, '导出结果', 3, NULL, 'hr:performance:export', NULL, NULL, 6, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 270);

-- 人事/超管：绩效管理 + 全部按钮
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM oa_role r
  JOIN oa_menu m ON m.id IN (263, 265, 266, 267, 268, 269, 270)
 WHERE r.role_code IN ('SUPER_ADMIN', 'HR', 'HR_MANAGER')
   AND r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 自助页：全部角色都能看「我的绩效」（带父目录，否则整枝被丢）
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM oa_role r
  JOIN oa_menu m ON m.id IN (230, 264)
 WHERE r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ---------- 11) 字典 ----------
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_perf_cycle_type', '绩效周期类型', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_perf_cycle_type');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_perf_stage', '绩效阶段', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_perf_stage');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_perf_indicator_type', '指标类型', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_perf_indicator_type');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_perf_appeal_status', '申诉状态', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_perf_appeal_status');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_cycle_type', '月度', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_cycle_type' AND item_value = '1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_cycle_type', '季度', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_cycle_type' AND item_value = '2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_cycle_type', '半年', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_cycle_type' AND item_value = '3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_cycle_type', '年度', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_cycle_type' AND item_value = '4');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_stage', '目标设定', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_stage' AND item_value = '1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_stage', '自评中', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_stage' AND item_value = '2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_stage', '上级评分', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_stage' AND item_value = '3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_stage', '校准', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_stage' AND item_value = '4');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_stage', '结果确认', '5', 5, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_stage' AND item_value = '5');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_stage', '已归档', '6', 6, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_stage' AND item_value = '6');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_indicator_type', '定量', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_indicator_type' AND item_value = '1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_indicator_type', '定性', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_indicator_type' AND item_value = '2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_indicator_type', '加分项', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_indicator_type' AND item_value = '3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_indicator_type', '扣分项', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_indicator_type' AND item_value = '4');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_appeal_status', '待处理', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_appeal_status' AND item_value = '1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_appeal_status', '维持原结果', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_appeal_status' AND item_value = '2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_perf_appeal_status', '调整', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_perf_appeal_status' AND item_value = '3');
