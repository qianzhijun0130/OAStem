package com.kellen.oa_system.workflow.support;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * 审批运行时的字面量、状态码与审批人策略码。
 *
 * <p>这些值同时被「状态机」「审批人解析」「超时扫描」「看板/列表展示」使用。
 * 尤其 {@code ASSIGNEE_*}：它既是 node_config 里的配置值，也是运行期 switch 的分支键，
 * 两处各写字面量，改一个忘一个就会出现「设计器配的是角色、运行期按人员解析」。</p>
 */
public final class ApprovalConstants {

    private ApprovalConstants() {
    }

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final Map<Integer, String> STATUS_NAME = Map.of(
            1, "草稿", 2, "审批中", 3, "已通过", 4, "已驳回", 5, "已撤销", 6, "已终止", 7, "已挂起");

    public static final Map<String, String> ACTION_NAME = Map.of(
            "submit", "提交", "approve", "同意", "reject", "驳回", "transfer", "转办",
            "withdraw", "撤销", "terminate", "终止", "suspend", "挂起", "resume", "恢复", "cc", "抄送");

    // ---------- 实例状态 ----------

    public static final int STATUS_DRAFT = 1;
    public static final int STATUS_APPROVING = 2;
    public static final int STATUS_PASSED = 3;
    public static final int STATUS_REJECTED = 4;
    public static final int STATUS_WITHDRAWN = 5;
    /** 已终止：由管理员/拥有实例权限者在审批中强制终止（区别于「已撤销」=发起人主动撤销） */
    public static final int STATUS_TERMINATED = 6;
    /** 已挂起：引擎侧 suspend，任务暂时退出候选人待办；恢复后回到审批中 */
    public static final int STATUS_SUSPENDED = 7;

    // ---------- 到期动作 ----------

    /** 仅标记（默认） */
    public static final int TIMEOUT_ACTION_MARK = 0;
    /** 提醒（写一条超时流转记录） */
    public static final int TIMEOUT_ACTION_REMIND = 1;
    /** 自动驳回 */
    public static final int TIMEOUT_ACTION_REJECT = 2;

    // ---------- 审批人规则类型 ----------

    public static final int ASSIGNEE_UNSET = 0;
    public static final int ASSIGNEE_USER = 1;
    public static final int ASSIGNEE_ROLE = 2;
    /** 发起人部门主管（层级可配） */
    public static final int ASSIGNEE_LEADER = 3;
    /** 申请人本人 */
    public static final int ASSIGNEE_APPLICANT = 4;
    /** 指定部门主管：取某个固定部门（assigneeValue 为部门 id）的主管 */
    public static final int ASSIGNEE_DEPT_LEADER = 5;

    /** 分支命中日志的动作标识：与 submit/approve/reject 等真实审批动作区分，详情展示时按此过滤 */
    public static final String ACTION_BRANCH = "branch";

    /** oa_approval_record.opinion 为 VARCHAR(500)，留 20 字符余量避免插入被截断报错 */
    public static final int BRANCH_DETAIL_MAX = 480;

    /**
     * 流程内建变量名，禁止被表单字段覆盖。
     *
     * <p>表单字段会被平铺成流程变量，若表单里恰好有同名字段（如 {@code title}），
     * 覆盖后会导致标题错乱或条件表达式引用到错误值。</p>
     */
    public static final Set<String> RESERVED_VARS = Set.of(
            "formData", "applicantId", "title",
            "applicantDeptId", "applicantDeptName", "applicantRoleCodes",
            "outcome", "approved", "opinion", "lastOutcome", "lastApproved");

    /** 超管角色 id（与 oa_role.id=1 / role_code='SUPER_ADMIN' 对齐）。审批节点角色下无用户时兜底给超管处理 */
    public static final long SUPER_ADMIN_ROLE_ID = 1L;

    /** 超管角色编码（与 oa_role.role_code 对齐） */
    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    /** 「流程实例」总览页权限码（菜单 218）：持有者可在该页查看任意单据详情 */
    public static final String PERM_INSTANCE = "system:approval:instance";
}
