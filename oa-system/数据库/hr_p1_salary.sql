-- =====================================================================
-- 人力资源 P1-1 薪酬：数据模型（2026-10-23）
--   薪资项 / 社保公积金政策 / 薪资档案（分段生效）/ 月度核算 / 明细 / 调整留痕
-- 口径见 开发文档/人力资源/03_薪酬管理.md：
--   · 日工资 = 月工资 / 21.75（不用当月天数，否则 2 月与 3 月同薪不同价）
--   · 个税按「累计预扣法」，逐月滚动
--   · 唯一索引一律带 active_flag（逻辑删后同键可复用）
-- =====================================================================
USE oa_system;

-- ---------- 1) 薪资项配置 ----------
CREATE TABLE IF NOT EXISTS oa_salary_item (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_code   VARCHAR(50)     NOT NULL COMMENT '项目编码，如 BASE/POST/PERF/OT/MEAL',
    item_name   VARCHAR(50)     NOT NULL COMMENT '项目名称',
    item_type   TINYINT         NOT NULL DEFAULT 1 COMMENT '1收入 2扣款',
    include_social TINYINT      NOT NULL DEFAULT 1 COMMENT '是否计入社保/公积金基数',
    include_tax TINYINT         NOT NULL DEFAULT 1 COMMENT '是否计入个税应税收入',
    editable    TINYINT         NOT NULL DEFAULT 0 COMMENT '1=核算时允许手工调整（留痕）',
    sort        INT             NOT NULL DEFAULT 0,
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark      VARCHAR(255)    DEFAULT NULL,
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT         NOT NULL DEFAULT 0,
    active_flag TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_code (item_code, active_flag),
    KEY idx_item_sort (sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '薪资项配置';

-- ---------- 2) 社保 / 公积金政策（按城市 + 年度） ----------
CREATE TABLE IF NOT EXISTS oa_social_policy (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    city            VARCHAR(50)     NOT NULL COMMENT '参保城市',
    policy_year     INT             NOT NULL COMMENT '政策年度',
    base_min        DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '社保基数下限',
    base_max        DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '社保基数上限（0=不封顶）',
    fund_base_min   DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '公积金基数下限',
    fund_base_max   DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '公积金基数上限（0=不封顶）',
    pension_personal    DECIMAL(6, 4) NOT NULL DEFAULT 0.0800 COMMENT '养老个人比例',
    pension_company     DECIMAL(6, 4) NOT NULL DEFAULT 0.1600 COMMENT '养老单位比例',
    medical_personal    DECIMAL(6, 4) NOT NULL DEFAULT 0.0200 COMMENT '医疗个人比例',
    medical_company     DECIMAL(6, 4) NOT NULL DEFAULT 0.0980 COMMENT '医疗单位比例',
    unemployment_personal DECIMAL(6, 4) NOT NULL DEFAULT 0.0050 COMMENT '失业个人比例',
    unemployment_company  DECIMAL(6, 4) NOT NULL DEFAULT 0.0050 COMMENT '失业单位比例',
    injury_company      DECIMAL(6, 4) NOT NULL DEFAULT 0.0020 COMMENT '工伤单位比例（个人不缴）',
    maternity_company   DECIMAL(6, 4) NOT NULL DEFAULT 0.0080 COMMENT '生育单位比例（个人不缴）',
    fund_personal       DECIMAL(6, 4) NOT NULL DEFAULT 0.1200 COMMENT '公积金个人比例',
    fund_company        DECIMAL(6, 4) NOT NULL DEFAULT 0.1200 COMMENT '公积金单位比例',
    remark          VARCHAR(255)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_city_year (city, policy_year, active_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '社保公积金政策';

-- ---------- 3) 薪资档案（分段生效：调薪=新增一条，历史不覆盖） ----------
CREATE TABLE IF NOT EXISTS oa_salary_profile (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    effective_date DATE         NOT NULL COMMENT '生效日期：取 <= 当期期末 的最新一条',
    base_salary DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '基本工资',
    post_salary DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '岗位工资',
    perf_salary DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '绩效工资（基数，实际按月绩效系数浮动）',
    allowance   DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '固定津贴',
    social_base DECIMAL(12, 2)  DEFAULT NULL COMMENT '社保基数（空=按应发工资）',
    fund_base   DECIMAL(12, 2)  DEFAULT NULL COMMENT '公积金基数（空=同社保基数）',
    city        VARCHAR(50)     DEFAULT NULL COMMENT '参保城市（空=取政策里的第一个）',
    special_deduct DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '专项附加扣除（月度）',
    change_reason VARCHAR(255)  DEFAULT NULL COMMENT '调薪原因',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT         NOT NULL DEFAULT 0,
    active_flag TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_effective (user_id, effective_date, active_flag),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '薪资档案（分段生效）';

-- ---------- 4) 月度核算主表 ----------
CREATE TABLE IF NOT EXISTS oa_salary_record (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    dept_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '核算时快照，避免员工调部门后历史被改',
    month           CHAR(7)         NOT NULL COMMENT 'yyyy-MM',
    base_salary     DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    post_salary     DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    perf_salary     DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '实发绩效（基数×系数）',
    allowance       DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    overtime_pay    DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '加班费',
    other_income    DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '其他收入（奖金/补发，手工录入）',
    gross_pay       DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '应发合计',
    attendance_deduct DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '考勤扣款（事假/病假/旷工）',
    social_personal DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '社保个人合计',
    fund_personal   DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '公积金个人',
    special_deduct  DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '专项附加扣除（计税用，不实际扣钱）',
    tax             DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '个人所得税（累计预扣）',
    other_deduct    DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '其他扣款（手工录入）',
    net_pay         DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '实发合计',
    social_company  DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '单位社保合计（成本口径）',
    fund_company    DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '单位公积金',
    social_base     DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '本次使用的社保基数（留痕，便于复核）',
    cumulative_taxable DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '本年累计应纳税所得额',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1草稿 2已复核 3已封账 4已发放',
    calc_time       DATETIME        DEFAULT NULL,
    review_time     DATETIME        DEFAULT NULL,
    review_by       BIGINT UNSIGNED DEFAULT NULL,
    pay_time        DATETIME        DEFAULT NULL,
    remark          VARCHAR(500)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_month (user_id, month, active_flag),
    KEY idx_month (month),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '月度薪资核算';

-- ---------- 5) 核算明细（逐项，工资条按它渲染） ----------
CREATE TABLE IF NOT EXISTS oa_salary_record_item (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_id   BIGINT UNSIGNED NOT NULL,
    item_code   VARCHAR(50)     NOT NULL,
    item_name   VARCHAR(50)     NOT NULL,
    item_type   TINYINT         NOT NULL DEFAULT 1 COMMENT '1收入 2扣款',
    amount      DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    sort        INT             NOT NULL DEFAULT 0,
    remark      VARCHAR(255)    DEFAULT NULL,
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_record (record_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '薪资核算明细';

-- ---------- 6) 手工调整留痕 ----------
CREATE TABLE IF NOT EXISTS oa_salary_adjust_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_id   BIGINT UNSIGNED NOT NULL,
    field_name  VARCHAR(50)     NOT NULL COMMENT '被调整的字段',
    field_label VARCHAR(50)     DEFAULT NULL,
    before_value DECIMAL(12, 2) DEFAULT NULL,
    after_value DECIMAL(12, 2) DEFAULT NULL,
    reason      VARCHAR(255)    NOT NULL COMMENT '调整原因（必填，审计要求）',
    operator_id BIGINT UNSIGNED DEFAULT NULL,
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_record (record_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '薪资手工调整留痕';

-- ---------- 7) 菜单与授权 ----------
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 255, 230, '薪酬管理', 2, '/hr/salary', 'hr:salary', 'hr/salary/index', 'Wallet', 6, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 255);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 256, 230, '我的工资条', 2, '/hr/my-salary', 'hr:salary:my', 'hr/my-salary/index', 'Receipt', 7, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 256);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 257, 255, '薪资档案', 3, NULL, 'hr:salary:profile', NULL, NULL, 1, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 257);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 258, 255, '核算', 3, NULL, 'hr:salary:calc', NULL, NULL, 2, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 258);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 259, 255, '复核', 3, NULL, 'hr:salary:review', NULL, NULL, 3, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 259);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 260, 255, '封账', 3, NULL, 'hr:salary:lock', NULL, NULL, 4, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 260);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 261, 255, '发放', 3, NULL, 'hr:salary:pay', NULL, NULL, 5, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 261);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 262, 255, '查看工资条', 3, NULL, 'hr:salary:view', NULL, NULL, 6, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 262);

-- 人事/超管：薪酬管理 + 全部按钮
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM oa_role r
  JOIN oa_menu m ON m.id IN (255, 257, 258, 259, 260, 261, 262)
 WHERE r.role_code IN ('SUPER_ADMIN', 'HR', 'HR_MANAGER')
   AND r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 普通员工：只看「我的工资条」
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, 256
  FROM oa_role r
 WHERE r.role_code = 'EMPLOYEE'
   AND r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 256);

-- ---------- 8) 字典 ----------
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_salary_status', '草稿', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_salary_status' AND item_value = '1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_salary_status', '已复核', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_salary_status' AND item_value = '2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_salary_status', '已封账', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_salary_status' AND item_value = '3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_salary_status', '已发放', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_salary_status' AND item_value = '4');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_salary_item_type', '收入项', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_salary_item_type' AND item_value = '1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_salary_item_type', '扣款项', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type = 'hr_salary_item_type' AND item_value = '2');

INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_salary_status', '薪资状态', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_salary_status');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_salary_item_type', '薪资项类型', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_salary_item_type');

-- ---------- 9) 初始数据：默认社保政策（2026 年，占位值，上线前按当地口径核对） ----------
INSERT INTO oa_social_policy (city, policy_year, base_min, base_max, fund_base_min, fund_base_max, remark)
SELECT '默认', 2026, 3613.00, 31884.00, 2420.00, 31884.00, '示例基数与比例，上线前必须按当地社保局口径替换'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_social_policy WHERE city = '默认' AND policy_year = 2026 AND is_deleted = 0);

-- ---------- 10) 初始数据：内置薪资项 ----------
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'BASE', '基本工资', 1, 1, 1, 0, 1, '取自薪资档案，参与社保基数' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'BASE' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'POST', '岗位工资', 1, 1, 1, 0, 2, '取自薪资档案' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'POST' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'PERF', '绩效工资', 1, 1, 1, 0, 3, '档案基数 × 当月绩效系数' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'PERF' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'ALLOW', '固定津贴', 1, 1, 1, 0, 4, '取自薪资档案' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'ALLOW' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'OT', '加班费', 1, 0, 1, 0, 5, '按加班类型倍率计算，不计入社保基数' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'OT' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'BONUS', '其他收入', 1, 0, 1, 1, 6, '奖金/补发，核算时可手工录入（留痕）' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'BONUS' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'ATT', '考勤扣款', 2, 0, 1, 0, 7, '事假/病假/旷工，按 21.75 折算' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'ATT' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'SOCIAL', '社保个人', 2, 0, 1, 0, 8, '养老+医疗+失业（个人部分）' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'SOCIAL' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'FUND', '公积金个人', 2, 0, 1, 0, 9, '个人缴存部分' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'FUND' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'TAX', '个人所得税', 2, 0, 0, 0, 10, '累计预扣法' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'TAX' AND is_deleted = 0);
INSERT INTO oa_salary_item (item_code, item_name, item_type, include_social, include_tax, editable, sort, remark)
SELECT 'OTHER', '其他扣款', 2, 0, 1, 1, 11, '手工录入（需填原因）' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_salary_item WHERE item_code = 'OTHER' AND is_deleted = 0);

-- ---------- 11) 修正：普通员工必须同时授予「人力资源」目录 ----------
-- 菜单树是「父不在可见集合就丢弃子节点」，只给 256 而不给 230 时，
-- 「我的工资条」会被整枝丢掉 → 页面 404（2026-10-23 实测踩到）。
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, 230
  FROM oa_role r
 WHERE r.role_code = 'EMPLOYEE'
   AND r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 230);

-- ---------- 12) 修正：自助类菜单授予「全部角色」 ----------
-- 背景：员工的实际角色是 TECH_ENGINEER / MARKET 这类业务角色，而 EMPLOYEE 只是
-- 「新建员工时的默认角色」。只把自助页授给 EMPLOYEE，业务角色的人就看不到
-- 「我的考勤 / 我的工资条」，也发不了请假单（2026-10-23 实测踩到）。
-- 自助页本身有数据范围兜底（只看自己），因此授予全部角色是安全的。
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM oa_role r
  JOIN oa_menu m ON m.id IN (230, 234, 235, 250, 256)
 WHERE r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
