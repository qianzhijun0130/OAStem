package com.kellen.oa_system.common;

import lombok.Getter;

/**
 * 统一错误码定义。
 *
 * <p>约定：HTTP 状态码沿用标准语义，业务错误使用 1xxx / 5xxx 段，
 * 前端可依据 code 做分支处理（如 401 跳登录、403 提示无权限）。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),

    // ---------- 通用客户端错误 ----------
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "无权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不被允许"),

    // ---------- 业务错误：用户与认证 1xxx ----------
    USERNAME_OR_PASSWORD_ERROR(1001, "用户名或密码错误"),
    USER_DISABLED(1002, "账号已被禁用，请联系管理员"),
    USER_LOCKED(1003, "账号已被锁定，请联系管理员"),
    USER_NOT_FOUND(1004, "用户不存在"),
    TOKEN_INVALID(1005, "令牌无效或已过期"),
    TOKEN_NOT_PROVIDED(1006, "缺少访问令牌"),
    PASSWORD_NOT_MATCH(1007, "原密码不正确"),
    /** 连续登录失败触发的临时锁定（Redis 计数），与 USER_LOCKED 的「账号状态被管理员锁定」区分开 */
    LOGIN_TOO_MANY_FAILURES(1008, "登录失败次数过多，账号已临时锁定，请稍后再试"),
    USERNAME_DUPLICATE(1108, "登录账号已存在"),
    WORK_NUMBER_DUPLICATE(1109, "工号已存在"),
    USER_CANNOT_OPERATE_SELF(1110, "不能删除或禁用当前登录账号"),
    USER_IMPORT_FILE_INVALID(1111, "导入文件格式不正确，请下载系统模板后重新填写"),
    USER_IMPORT_EMPTY(1112, "导入文件中没有可导入的数据"),
    WORK_NUMBER_GENERATE_FAILED(1113, "工号生成失败，请联系管理员检查部门工号前缀"),
    /** 被管理员强制下线：code 仍为 401，前端据此直接清理本地令牌并跳登录页；message 用来区分原因 */
    TOKEN_KICKED(401, "账号已被管理员强制下线，请重新登录"),
    ONLINE_USER_NOT_FOUND(1114, "该用户当前不在线"),
    ONLINE_KICK_SELF_FORBIDDEN(1115, "不能强制下线当前登录账号"),
    ONLINE_KICK_UNAVAILABLE(1116, "Redis 不可用，暂时无法执行强制下线"),

    // ---------- 业务错误：组织架构与权限 2xxx ----------
    DEPT_NOT_FOUND(2001, "部门不存在"),
    DEPT_CODE_DUPLICATE(2002, "部门编码已存在"),
    DEPT_HAS_CHILDREN(2003, "存在下级部门，不能删除"),
    DEPT_HAS_USER(2004, "部门下存在用户，不能删除"),
    DEPT_PARENT_INVALID(2005, "上级部门不能是自身或其下级"),
    DEPT_WORK_NO_PREFIX_DUPLICATE(2006, "工号前缀已被其他部门使用，请更换"),
    DEPT_DISABLED(2007, "部门已停用，请选择启用状态的部门"),
    ROLE_NOT_FOUND(2101, "角色不存在"),
    ROLE_CODE_DUPLICATE(2102, "角色编码已存在"),
    ROLE_IN_USE(2103, "角色下存在用户，不能删除"),
    MENU_NOT_FOUND(2201, "菜单不存在"),
    MENU_HAS_CHILDREN(2202, "存在下级菜单，不能删除"),
    MENU_CODE_DUPLICATE(2203, "菜单权限标识已存在"),
    MENU_TYPE_INVALID(2204, "菜单类型不合法"),
    MENU_FIELDS_INVALID(2205, "菜单配置不完整"),

    // ---------- 业务错误：数据字典 23xx ----------
    DICT_TYPE_NOT_FOUND(2301, "字典类型不存在"),
    DICT_TYPE_DUPLICATE(2302, "字典类型编码已存在"),
    DICT_TYPE_HAS_DATA(2303, "该字典类型下存在数据项，请先删除数据项"),
    DICT_DATA_NOT_FOUND(2311, "字典数据项不存在"),
    DICT_DATA_DUPLICATE(2312, "该字典类型下数据值已存在"),
    NOTICE_NOT_FOUND(2401, "公告不存在"),
    NOTICE_STATUS_ILLEGAL(2402, "当前公告状态不允许该操作"),
    NOTICE_NOT_PUBLISHED(2403, "公告尚未发布"),
    NOTICE_PUBLISHED_CANNOT_EDIT(2404, "已发布的公告不能修改内容，请先下架"),
    NOTICE_SCOPE_REQUIRED(2405, "指定发布范围时至少要选择一个部门或人员"),

    // ---------- 业务错误：低代码表单 25xx ----------
    FORM_NOT_FOUND(2501, "表单不存在"),
    FORM_CODE_DUPLICATE(2502, "表单编码已存在"),
    FORM_RULE_INVALID(2503, "表单规则格式非法，请使用设计器生成规则"),
    FORM_IN_USE(2504, "该表单已被流程节点绑定，无法删除"),
    FLOW_NOT_FOUND_FOR_BIND(2505, "待绑定的流程模板不存在"),
    NODE_FORM_NOT_BOUND(2506, "该节点尚未绑定表单，无法配置字段权限"),
    FIELD_PERM_INVALID(2507, "字段权限配置包含该表单不存在的字段，请刷新后重试"),
    FLOW_CONDITION_INVALID(2508, "分支条件配置不合法：条件字段、运算符、取值或目标连线有误"),
    FLOW_CONDITION_EXPR_UNSAFE(2509, "条件表达式包含不允许的语法，请检查字段名与比较值"),

    // ---------- 业务错误：流程引擎与审批 3xxx ----------
    FLOW_DEF_NOT_FOUND(3001, "流程模板不存在"),
    FLOW_DEF_DUPLICATE(3002, "流程编码已存在"),
    FLOW_DEF_DISABLED(3003, "流程模板已停用，无法发起申请"),
    FLOW_NOT_DEPLOYED(3004, "流程定义尚未部署，无法发起申请"),
    FLOW_BPMN_INVALID(3005, "BPMN 文件解析失败或缺少必要节点"),
    FLOW_KEY_CONFLICT(3006, "流程标识已被其他流程模板占用"),
    FLOW_DEF_IN_USE(3007, "该流程模板下存在未结束的审批实例，无法删除"),
    INSTANCE_NOT_FOUND(3101, "审批单不存在"),
    INSTANCE_ALREADY_DONE(3102, "审批单已结束，无法操作"),
    TASK_NOT_FOUND(3103, "审批任务不存在"),
    TASK_NOT_CANDIDATE(3104, "当前用户不是该任务的审批人"),
    INSTANCE_CANNOT_WITHDRAW(3105, "仅审批中的申请可以撤销"),
    INSTANCE_CANNOT_SUSPEND(3106, "仅审批中的申请可以挂起"),
    INSTANCE_ALREADY_SUSPENDED(3107, "审批单已挂起，无需重复操作"),
    INSTANCE_NOT_SUSPENDED(3108, "仅已挂起的审批单可以恢复"),

    // ---------- 业务错误：文件存储 4xxx ----------
    FILE_EMPTY(4001, "上传文件不能为空"),
    FILE_TYPE_NOT_ALLOWED(4002, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(4003, "文件大小超出限制"),
    FILE_NOT_FOUND(4004, "文件不存在或已被删除"),
    FILE_UPLOAD_FAILED(4005, "文件上传失败，请稍后重试"),
    FILE_PATH_INVALID(4006, "文件路径非法"),

    // ---------- 业务错误：人力资源 6xxx ----------
    // 61xx 组织与员工档案
    HR_POSITION_CODE_DUPLICATE(6101, "岗位编码已存在"),
    HR_POSITION_IN_USE(6102, "该岗位下有在职员工，请先调岗"),
    HR_EMPLOYMENT_STATUS_INVALID(6103, "任职状态不允许该跳转"),
    HR_CONTRACT_DATE_OVERLAP(6104, "合同起止时间与已有合同重叠"),
    HR_EMPLOYEE_DUPLICATE(6105, "手机号或身份证已存在员工档案"),
    HR_POSITION_NOT_FOUND(6106, "岗位不存在或已被删除"),
    // 62xx 考勤
    HR_ATTENDANCE_NOT_SCHEDULED(6201, "该日期未排班，无法生成考勤"),
    HR_CARD_SUPPLEMENT_EXPIRED(6202, "补卡只允许回溯 30 天"),
    HR_CARD_SUPPLEMENT_LIMIT(6203, "本月补卡次数已达上限"),
    HR_ATTENDANCE_SETTLED(6204, "该日期已日结，不可直接修改"),
    HR_ATTENDANCE_LOCKED(6205, "该月已封账，需超级管理员解封"),
    HR_ATTENDANCE_ALREADY_CLOCKED(6206, "今日已完成该方向的打卡"),
    HR_FLOW_NOT_DEPLOYED(6207, "该审批流程尚未发布，请先在「流程管理 → 流程模板」发布后再发起"),
    // 63xx 假勤
    HR_LEAVE_QUOTA_NOT_ENOUGH(6301, "该假别可用额度不足"),
    HR_LEAVE_TIME_OVERLAP(6302, "与已有请假单时间重叠"),
    HR_LEAVE_CANCEL_NOT_ALLOWED(6303, "仅已通过的请假单可销假，且只能销假一次"),
    HR_SCHEDULE_INVALID(6304, "排班日期或班次不合法"),
    HR_LEAVE_POLICY_EXCEEDED(6305, "请假天数超出该假别的法定上限"),
    HR_LEAVE_DAYS_INVALID(6306, "请假天数不合法：至少 0.5 天，且结束时间必须晚于开始时间"),
    HR_LEAVE_ATTACHMENT_REQUIRED(6307, "病假 3 天以上必须上传诊断证明"),
    HR_OVERTIME_EXCEED_LIMIT(6308, "单月加班不得超过 36 小时"),
    HR_TRIP_LEAVE_CONFLICT(6309, "出差时间与已有请假时间冲突"),
    HR_CARD_ALREADY_EXISTS(6310, "该日期该方向已有补卡记录"),
    HR_LEAVE_POLICY_DUPLICATE(6311, "该城市该年度该假别的政策已存在"),
    HR_LEAVE_POLICY_TYPE_INVALID(6312, "该假别不适用地方政策配置（仅婚假/产假/陪产假/丧假）"),

    // ---------- 业务错误：薪资 64xx ----------
    HR_SALARY_PROFILE_MISSING(6401, "存在没有生效薪资档案的员工，请先维护薪资档案"),
    HR_SALARY_ATTENDANCE_UNLOCKED(6402, "该月考勤尚未封账，封账后才能核算薪资（否则考勤一变工资就对不上）"),
    HR_SALARY_MONTH_LOCKED(6403, "该月薪资已封账或已发放，不能再修改"),
    HR_SALARY_STATUS_INVALID(6404, "当前状态不允许该操作"),
    HR_SALARY_ADJUST_REASON_REQUIRED(6405, "手工调整必须填写原因"),
    HR_SALARY_ITEM_DUPLICATE(6406, "薪资项编码已存在"),
    HR_SALARY_POLICY_DUPLICATE(6407, "该城市该年度的社保政策已存在"),
    HR_SALARY_RECORD_NOT_FOUND(6408, "薪资记录不存在"),
    HR_SALARY_POLICY_MISSING(6409, "缺少该年度的社保政策，请先在「社保政策」中配置"),
    HR_SALARY_NO_DATA(6410, "该月没有可核算的数据，请先维护薪资档案与考勤月结"),

    // ---------- 业务错误：绩效 65xx ----------
    PERF_WEIGHT_NOT_100(6501, "指标权重合计必须等于 100%"),
    PERF_INDICATOR_DUPLICATE(6502, "指标编码已存在"),
    PERF_PLAN_NOT_FOUND(6503, "绩效方案不存在"),
    PERF_CYCLE_STAGE_INVALID(6504, "当前阶段不允许该操作"),
    PERF_TARGET_NOT_LOCKED(6505, "目标尚未审核通过，不能进入自评"),
    PERF_SHEET_NOT_FOUND(6506, "考核表不存在"),
    PERF_NOT_LEADER(6507, "只有直属上级或人事可以评分"),
    PERF_SCORE_OUT_OF_RANGE(6508, "分数超出允许范围"),
    PERF_CALIBRATE_REASON_REQUIRED(6509, "校准调整必须填写理由"),
    PERF_APPEAL_DUPLICATE(6510, "该考核表已提交过申诉，只能申诉一次"),
    PERF_APPEAL_WINDOW_CLOSED(6511, "已超过申诉期限（结果确认后 5 个工作日内）"),
    PERF_ARCHIVED_READONLY(6512, "该周期已归档，结果只读"),
    PERF_CYCLE_DATE_OVERLAP(6513, "同方案下周期日期不能重叠"),
    PERF_PLAN_IN_USE(6514, "方案已被周期引用，不能删除，可改为停用"),
    PERF_NO_SHEET(6516, "当期没有你的考核表"),
    PERF_TARGET_REQUIRED(6517, "请先填写目标值再提交"),
    PERF_GRADE_MISSING(6518, "方案未配置等级映射，无法定级"),

    // ---------- 招聘 66xx ----------
    RECRUIT_CANDIDATE_DUPLICATE(6601, "该手机号已有候选人记录"),
    RECRUIT_STAGE_INVALID(6602, "招聘阶段不允许该操作"),
    OFFER_EXPIRED(6603, "Offer 已过期，无法操作"),
    CANDIDATE_NOT_AUTHORIZED(6604, "未获得候选人授权，不可保存简历"),
    DEMAND_DEPT_INVALID(6605, "需求部门不存在或已停用"),
    DEMAND_POSITION_INVALID(6606, "需求岗位不存在或已停用"),
    INTERVIEW_FEEDBACK_INCOMPLETE(6607, "还有面试官未提交评价，不能汇总"),
    INTERVIEW_CONTINUE_REASON_REQUIRED(6608, "存在「不通过」评价，继续推进必须填写理由"),
    OFFER_STATUS_INVALID(6609, "Offer 状态不允许该操作"),
    CONVERT_TARGET_REQUIRED(6610, "该手机号已有员工档案，请选择复用或新建"),
    DEMAND_APPROVED_READONLY(6611, "需求已通过审批，不能直接修改"),
    CANDIDATE_STAGE_FINAL(6612, "候选人已入职，阶段不可再变更"),

    // ---------- 培训 67xx ----------
    TRAIN_SESSION_FULL(6701, "名额已满，已进入候补"),
    TRAIN_ENROLL_CLOSED(6702, "报名已截止或期次未开始"),
    TRAIN_ALREADY_ENROLLED(6703, "你已报名该期次"),
    TRAIN_ABSENCE_RESTRICTED(6704, "累计爽约 3 次，1 个月内不能报名"),
    TRAIN_SESSION_STARTED(6705, "开课已开始或已结束，不能取消报名"),
    TRAIN_CANCEL_TOO_LATE(6706, "距开课不足 24 小时，取消将计为爽约"),
    TRAIN_SCORE_INVALID(6707, "成绩须在 0~100 之间"),
    TRAIN_COURSE_INVALID(6708, "课程不存在或已停用"),
    TRAIN_SESSION_INVALID(6709, "培训期次不存在"),
    TRAIN_NOT_CHECKED_IN(6710, "未签到不能录入成绩"),

    // ---------- 系统错误 5xxx ----------
    SYSTEM_ERROR(5000, "系统内部错误，请稍后重试");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
