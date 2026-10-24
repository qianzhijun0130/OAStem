-- =====================================================================
-- 人力资源 P2 招聘与培训：数据模型（2026-10-23）
--   招聘：需求 / 职位 / 候选人 / 面试 / 面试评价 / Offer
--   培训：课程 / 开课期次 / 报名（含签到与成绩）
-- 口径见 开发文档/人力资源/05_招聘与培训.md：
--   · 需求人数超编制缺口「提示但��拦截」——超编招聘现实中存在，由审批人判断
--   · 候选人手机号唯一：同一手机号重复录入合并到同一人，不产生两条
--   · 阶段只允许前进或淘汰/放弃；「已入职」不可回退，回退需 HR 权限且写日志
--   · 面试结论需所有面试官提交后才能汇总；有「不通过」时继续必须填理由
--   · offer 有效期默认 5 个工作日，过期自动置「已放弃」，过期 offer 不能转员工
--   · 转员工按「手机号 + 姓名」查重，命中已有档案提示复用/新建，选择留痕
--   · 培训名额满后普通员工报名进候补，HR 指派可超额并填原因
--   · 学时按签到计算，不按报名（报名不算学会）
-- =====================================================================
USE oa_system;

-- ---------- 1) 招聘需求 ----------
CREATE TABLE IF NOT EXISTS oa_recruit_demand (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    demand_code     VARCHAR(50)     NOT NULL COMMENT '需求编号 ZP-{yyyyMM}-{4位流水}',
    dept_id         BIGINT UNSIGNED NOT NULL COMMENT '需求部门',
    position_id     BIGINT UNSIGNED NOT NULL COMMENT '需求岗位',
    head_count      INT             NOT NULL DEFAULT 1 COMMENT '招聘人数',
    urgency         TINYINT         NOT NULL DEFAULT 1 COMMENT '1普通 2紧急',
    expect_date     DATE            DEFAULT NULL COMMENT '期望到岗日期',
    education       VARCHAR(50)     DEFAULT NULL COMMENT '学历要求',
    experience      VARCHAR(100)    DEFAULT NULL COMMENT '经验年限要求',
    skill_require   VARCHAR(500)    DEFAULT NULL COMMENT '技能要求',
    salary_min      DECIMAL(12, 2)  DEFAULT NULL COMMENT '薪资范围下限',
    salary_max      DECIMAL(12, 2)  DEFAULT NULL COMMENT '薪资范围上限',
    reason_type     TINYINT         NOT NULL DEFAULT 1 COMMENT '1新增 2替补 3储备',
    reason          VARCHAR(500)    DEFAULT NULL COMMENT '招聘原因说明',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1待审批 2招聘中 3已暂停 4已完成 5已关闭 6已驳回',
    hr_id           BIGINT UNSIGNED DEFAULT NULL COMMENT '负责HR',
    approve_time    DATETIME        DEFAULT NULL COMMENT '审批通过时间（算招聘周期起点）',
    finish_time     DATETIME        DEFAULT NULL COMMENT '完成时间（算招聘周期终点）',
    remark          VARCHAR(500)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_demand_code (demand_code, active_flag),
    KEY idx_dept (dept_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '招聘需求';

-- ---------- 2) 职位 ----------
CREATE TABLE IF NOT EXISTS oa_recruit_post (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    demand_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联需求（可空：独立发布）',
    post_name       VARCHAR(100)    NOT NULL COMMENT '职位名称',
    city            VARCHAR(50)     DEFAULT NULL COMMENT '工作地点',
    salary_min      DECIMAL(12, 2)  DEFAULT NULL COMMENT '薪资范围下限',
    salary_max      DECIMAL(12, 2)  DEFAULT NULL COMMENT '薪资范围上限',
    head_count      INT             NOT NULL DEFAULT 1 COMMENT '招聘人数',
    channel         TINYINT         NOT NULL DEFAULT 1 COMMENT '1内推 2官网 3招聘平台',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1草稿 2已发布 3已下线',
    hr_id           BIGINT UNSIGNED DEFAULT NULL COMMENT '负责HR',
    publish_time    DATETIME        DEFAULT NULL,
    remark          VARCHAR(500)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    KEY idx_demand (demand_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '招聘职位';

-- ---------- 3) 候选人 ----------
CREATE TABLE IF NOT EXISTS oa_recruit_candidate (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)     NOT NULL COMMENT '姓名',
    mobile          VARCHAR(20)     NOT NULL COMMENT '手机号（唯一，重复投递合并）',
    email           VARCHAR(100)    DEFAULT NULL,
    gender          TINYINT         DEFAULT NULL COMMENT '1男 2女',
    birth_date      DATE            DEFAULT NULL,
    post_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '应聘职位',
    demand_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联需求',
    resume_file_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '简历附件 oa_file.id',
    source          TINYINT         NOT NULL DEFAULT 1 COMMENT '1内推 2招聘平台 3猎头 4校招 5主动投递',
    referrer_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '内推人（来源=内推时必填）',
    stage           TINYINT         NOT NULL DEFAULT 1 COMMENT '1待筛选 2初试 3复试 4终试 5待定 6已发offer 7已入职 8已淘汰 9已放弃',
    reject_reason   VARCHAR(200)    DEFAULT NULL COMMENT '淘汰原因（stage=8 时必填）',
    reject_type     TINYINT         DEFAULT NULL COMMENT '淘汰原因类型（字典 hr_reject_reason）',
    expect_salary   DECIMAL(12, 2)  DEFAULT NULL COMMENT '期望薪资',
    current_salary  DECIMAL(12, 2)  DEFAULT NULL COMMENT '当前薪资',
    education       VARCHAR(50)     DEFAULT NULL,
    experience      VARCHAR(100)    DEFAULT NULL,
    authorized      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已获授权（未授权不可保存）',
    talent_pool     TINYINT         NOT NULL DEFAULT 0 COMMENT '1进人才库 0不进',
    pool_expire_date DATE           DEFAULT NULL COMMENT '简历保留到期日（到期脱敏）',
    user_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '转为员工后关联 oa_user.id',
    remark          VARCHAR(500)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_candidate_mobile (mobile, active_flag),
    KEY idx_stage (stage),
    KEY idx_post (post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '招聘候选人';

-- ---------- 4) 面试安排 ----------
CREATE TABLE IF NOT EXISTS oa_recruit_interview (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    candidate_id    BIGINT UNSIGNED NOT NULL,
    post_id         BIGINT UNSIGNED DEFAULT NULL,
    round_no        INT             NOT NULL DEFAULT 1 COMMENT '轮次',
    interview_time  DATETIME        NOT NULL,
    way             TINYINT         NOT NULL DEFAULT 1 COMMENT '1现场 2电话 3视频',
    location        VARCHAR(200)    DEFAULT NULL COMMENT '地点或线上链接',
    interviewer_ids VARCHAR(500)    NOT NULL COMMENT '面试官ID，逗号分隔',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1待面试 2已汇总 3已取消',
    conclusion      TINYINT         DEFAULT NULL COMMENT '1通过 2待定 3不通过',
    summary         VARCHAR(1000)   DEFAULT NULL COMMENT '汇总评语',
    continue_reason VARCHAR(500)    DEFAULT NULL COMMENT '有「不通过」仍继续推进的理由',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_candidate_round (candidate_id, post_id, round_no, active_flag),
    KEY idx_candidate (candidate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '招聘面试安排';

-- ---------- 5) 面试评价（每个面试官一行） ----------
CREATE TABLE IF NOT EXISTS oa_recruit_feedback (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    interview_id    BIGINT UNSIGNED NOT NULL,
    interviewer_id  BIGINT UNSIGNED NOT NULL,
    professional    TINYINT         DEFAULT NULL COMMENT '专业能力 1~5',
    communication   TINYINT         DEFAULT NULL COMMENT '沟通表达 1~5',
    logic_score     TINYINT         DEFAULT NULL COMMENT '逻辑思维 1~5',
    culture_match   TINYINT         DEFAULT NULL COMMENT '文化匹配 1~5',
    conclusion      TINYINT         NOT NULL COMMENT '1通过 2待定 3不通过',
    comment         VARCHAR(1000)   DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_interview_interviewer (interview_id, interviewer_id, active_flag),
    KEY idx_interview (interview_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '招聘面试评价';

-- ---------- 6) Offer ----------
CREATE TABLE IF NOT EXISTS oa_recruit_offer (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    offer_no        VARCHAR(50)     NOT NULL COMMENT 'Offer 编号',
    candidate_id    BIGINT UNSIGNED NOT NULL,
    post_id         BIGINT UNSIGNED DEFAULT NULL,
    position_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '岗位',
    position_name   VARCHAR(100)    DEFAULT NULL COMMENT '职级/岗位名称快照',
    salary          DECIMAL(12, 2)  NOT NULL COMMENT '薪资',
    probation_months INT            NOT NULL DEFAULT 3 COMMENT '试用期月数',
    report_date     DATE            DEFAULT NULL COMMENT '报到日期',
    expire_date     DATE            NOT NULL COMMENT '有效期截止（默认 5 个工作日）',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1待审批 2已发出 3已接受 4已拒绝 5已过期 6已作废',
    send_way        TINYINT         DEFAULT NULL COMMENT '1邮件 2短信 3纸质',
    send_time       DATETIME        DEFAULT NULL,
    accept_time     DATETIME        DEFAULT NULL,
    refuse_reason   VARCHAR(500)    DEFAULT NULL,
    user_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '转为员工后关联 oa_user.id',
    convert_log     VARCHAR(500)    DEFAULT NULL COMMENT '转员工留痕（复用/新建 + 操作人）',
    remark          VARCHAR(500)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_offer_no (offer_no, active_flag),
    KEY idx_candidate (candidate_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '招聘 Offer';

-- ---------- 7) 培训课程 ----------
CREATE TABLE IF NOT EXISTS oa_train_course (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_code     VARCHAR(50)     NOT NULL COMMENT '课程编码',
    course_name     VARCHAR(100)    NOT NULL,
    train_type      TINYINT         NOT NULL DEFAULT 1 COMMENT '1新员工 2岗位技能 3管理能力 4合规安全 5通用素养',
    teacher_type    TINYINT         NOT NULL DEFAULT 1 COMMENT '1内部讲师 2外部讲师',
    teacher_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '内部讲师 oa_user.id',
    teacher_name    VARCHAR(100)    DEFAULT NULL COMMENT '外部讲师姓名',
    teacher_org     VARCHAR(100)    DEFAULT NULL COMMENT '外部讲师机构',
    hours           DECIMAL(6, 1)   NOT NULL DEFAULT 0 COMMENT '学时（小时）',
    dept_ids        VARCHAR(500)    DEFAULT NULL COMMENT '适用部门（空=全部）',
    series          VARCHAR(50)     DEFAULT NULL COMMENT '适用岗位序列（空=全部）',
    required        TINYINT         NOT NULL DEFAULT 0 COMMENT '1必修 0选修',
    pass_score      DECIMAL(6, 1)   DEFAULT 60 COMMENT '及格线',
    file_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '教材附件',
    cost            DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '培训成本（外部讲师/场地）',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 2停用',
    remark          VARCHAR(500)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_code (course_code, active_flag),
    KEY idx_type (train_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '培训课程';

-- ---------- 8) 开课期次 ----------
CREATE TABLE IF NOT EXISTS oa_train_session (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id       BIGINT UNSIGNED NOT NULL,
    session_name    VARCHAR(100)    NOT NULL COMMENT '期次名（如 2026 第 3 期）',
    start_time      DATETIME        NOT NULL,
    end_time        DATETIME        NOT NULL,
    location        VARCHAR(200)    DEFAULT NULL,
    online_link     VARCHAR(500)    DEFAULT NULL,
    capacity        INT             NOT NULL DEFAULT 0 COMMENT '名额（0=不限）',
    enrolled_count  INT             NOT NULL DEFAULT 0 COMMENT '已报名数（不含候补）',
    wait_count      INT             NOT NULL DEFAULT 0 COMMENT '候补数',
    enroll_deadline DATETIME        DEFAULT NULL COMMENT '报名截止',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1报名中 2进行中 3已结束 4已取消',
    checkin_way     TINYINT         NOT NULL DEFAULT 1 COMMENT '1二维码 2手工点名',
    remark          VARCHAR(500)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    KEY idx_course (course_id),
    KEY idx_start (start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '培训开课期次';

-- ---------- 9) 报名（含签到与成绩） ----------
CREATE TABLE IF NOT EXISTS oa_train_enroll (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id      BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    enroll_way      TINYINT         NOT NULL DEFAULT 1 COMMENT '1自助报名 2HR指派',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1已报名 2候补 3已取消 4已签到 5缺勤 6已完成 7爽约',
    assign_reason   VARCHAR(500)    DEFAULT NULL COMMENT 'HR 指派原因',
    checkin_time    DATETIME        DEFAULT NULL,
    real_hours      DECIMAL(6, 1)   NOT NULL DEFAULT 0 COMMENT '实际学时（按签到计算）',
    score           DECIMAL(6, 1)   DEFAULT NULL COMMENT '考核成绩',
    assess_way      TINYINT         DEFAULT NULL COMMENT '1考试 2作业 3讲师评估 4免考',
    passed          TINYINT         DEFAULT NULL COMMENT '1及格 0不及格',
    satisfied       TINYINT         DEFAULT NULL COMMENT '满意度 1~5',
    cert_file_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '证书附件',
    cancel_time     DATETIME        DEFAULT NULL,
    remark          VARCHAR(500)    DEFAULT NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_user (session_id, user_id, active_flag),
    KEY idx_user (user_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '培训报名与签到';

-- ---------- 10) 爽约记录（累计 3 次限制报名 1 个月） ----------
CREATE TABLE IF NOT EXISTS oa_train_absence (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    session_id      BIGINT UNSIGNED DEFAULT NULL,
    enroll_id       BIGINT UNSIGNED DEFAULT NULL,
    absence_time    DATETIME        NOT NULL,
    restricted_until DATE           DEFAULT NULL COMMENT '限制报名截止日',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT         NOT NULL DEFAULT 0,
    active_flag     TINYINT GENERATED ALWAYS AS (IF(is_deleted = 0, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_time (absence_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '培训爽约记录';

-- ---------- 11) 字典 ----------
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_recruit_stage', '招聘阶段', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_recruit_stage');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_candidate_source', '候选人来源', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_candidate_source');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_recruit_reason', '招聘原因', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_recruit_reason');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_reject_reason', '淘汰原因', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_reject_reason');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_train_type', '培训类型', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_train_type');
INSERT INTO oa_dict_type (dict_type, dict_name, status, is_deleted)
SELECT 'hr_train_session_status', '培训期次状态', 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_type WHERE dict_type = 'hr_train_session_status');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '待筛选', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '初试', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '复试', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '终试', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='4');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '待定', '5', 5, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='5');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '已发offer', '6', 6, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='6');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '已入职', '7', 7, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='7');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '已淘汰', '8', 8, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='8');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_stage', '已放弃', '9', 9, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_stage' AND item_value='9');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_candidate_source', '内推', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_candidate_source' AND item_value='1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_candidate_source', '招聘平台', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_candidate_source' AND item_value='2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_candidate_source', '猎头', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_candidate_source' AND item_value='3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_candidate_source', '校招', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_candidate_source' AND item_value='4');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_candidate_source', '主动投递', '5', 5, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_candidate_source' AND item_value='5');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_reason', '新增', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_reason' AND item_value='1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_reason', '替补', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_reason' AND item_value='2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_recruit_reason', '储备', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_recruit_reason' AND item_value='3');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_reject_reason', '技能不匹配', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_reject_reason' AND item_value='1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_reject_reason', '经验不足', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_reject_reason' AND item_value='2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_reject_reason', '学历不符', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_reject_reason' AND item_value='3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_reject_reason', '薪资不匹配', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_reject_reason' AND item_value='4');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_reject_reason', '文化不匹配', '5', 5, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_reject_reason' AND item_value='5');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_reject_reason', '候选人放弃', '6', 6, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_reject_reason' AND item_value='6');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_type', '新员工入职', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_type' AND item_value='1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_type', '岗位技能', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_type' AND item_value='2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_type', '管理能力', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_type' AND item_value='3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_type', '合规安全', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_type' AND item_value='4');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_type', '通用素养', '5', 5, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_type' AND item_value='5');

INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_session_status', '报名中', '1', 1, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_session_status' AND item_value='1');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_session_status', '进行中', '2', 2, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_session_status' AND item_value='2');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_session_status', '已结束', '3', 3, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_session_status' AND item_value='3');
INSERT INTO oa_dict_data (dict_type, item_label, item_value, sort, status, is_deleted)
SELECT 'hr_train_session_status', '已取消', '4', 4, 1, 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_dict_data WHERE dict_type='hr_train_session_status' AND item_value='4');

-- ---------- 12) 菜单与授权 ----------
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 271, 230, '招聘管理', 2, '/hr/recruit', 'hr:recruit', 'hr/recruit/index', 'UserPlus', 10, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 271);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 272, 230, '培训管理', 2, '/hr/training', 'hr:training', 'hr/training/index', 'GraduationCap', 11, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 272);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 273, 230, '我的培训', 2, '/hr/my-training', 'hr:training:my', 'hr/my-training/index', 'BookOpen', 12, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 273);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 274, 271, '需求维护', 3, NULL, 'hr:recruit:demand', NULL, NULL, 1, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 274);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 275, 271, '职位维护', 3, NULL, 'hr:recruit:post', NULL, NULL, 2, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 275);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 276, 271, '候选人管理', 3, NULL, 'hr:recruit:candidate', NULL, NULL, 3, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 276);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 277, 271, '面试管理', 3, NULL, 'hr:recruit:interview', NULL, NULL, 4, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 277);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 278, 271, 'Offer管理', 3, NULL, 'hr:recruit:offer', NULL, NULL, 5, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 278);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 279, 271, '简历导入', 3, NULL, 'hr:recruit:import', NULL, NULL, 6, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 279);

INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 280, 272, '课程维护', 3, NULL, 'hr:training:course', NULL, NULL, 1, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 280);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 281, 272, '开课管理', 3, NULL, 'hr:training:session', NULL, NULL, 2, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 281);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 282, 272, '报名指派', 3, NULL, 'hr:training:enroll', NULL, NULL, 3, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 282);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 283, 272, '成绩录入', 3, NULL, 'hr:training:score', NULL, NULL, 4, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 283);
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 284, 272, '培训统计', 3, NULL, 'hr:training:stat', NULL, NULL, 5, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 284);

-- 人事/超管：招聘 + 培训全部按钮
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM oa_role r
  JOIN oa_menu m ON m.id IN (271, 272, 274, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284)
 WHERE r.role_code IN ('SUPER_ADMIN', 'HR', 'HR_MANAGER')
   AND r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 自助页：全部角色都能看「我的培训」（带父目录，否则整枝被丢）
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM oa_role r
  JOIN oa_menu m ON m.id IN (230, 273)
 WHERE r.is_deleted = 0
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ---------- 13) 补列（早期版本建的表缺 remark，实体继承了 remark 字段） ----------
-- 说明：oa_recruit_interview / oa_recruit_feedback / oa_train_absence 三张表
--       在实体里声明了 remark，但初版建表脚本漏了这列 → 插入时 SQL 报
--       「Unknown column 'remark'」。这里用 information_schema 判断后再补，保证幂等。
--       另：oa_train_absence 实体继承了 AuditEntity（含 update_time 填充），
--       初版建表同样漏了这列 → 查询报「Unknown column 'update_time'」。
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'oa_recruit_interview' AND column_name = 'remark') = 0,
  'ALTER TABLE oa_recruit_interview ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT ''备注''',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'oa_recruit_feedback' AND column_name = 'remark') = 0,
  'ALTER TABLE oa_recruit_feedback ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT ''备注''',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'oa_train_absence' AND column_name = 'remark') = 0,
  'ALTER TABLE oa_train_absence ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT ''备注''',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'oa_train_absence' AND column_name = 'update_time') = 0,
  'ALTER TABLE oa_train_absence ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
