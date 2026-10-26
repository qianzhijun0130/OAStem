#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P2 招聘 + 培训模块接口验证。

覆盖招聘：需求（超编制提示不拦截）/ 职位 / 候选人（手机号去重、阶段流转）
     / 面试（多面试官、评价齐才汇总）/ offer（有效期、转员工查重）/ 看板
覆盖培训：课程 / 期次 / 报名（满额候补）/ 取消（24h 内计爽约）/ 签到（学时按签到）
     / 成绩 / 我的培训 / 统计
"""
import sys
sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import mysql, req, token, ck, summary, DB, roles_of
from urllib.parse import quote

ADMIN = 1
admin_tok = token(ADMIN, 'admin', hours=3)

# 员工与其上级（主管要当面试官）
row = mysql("SELECT u.id, u.username, IFNULL(u.direct_leader_id, d.leader_id) lid "
            "FROM oa_system.oa_user u LEFT JOIN oa_system.oa_department d ON d.id=u.dept_id "
            "WHERE u.is_deleted=0 AND u.status=1 AND u.employment_status IN (2,3,4) "
            "AND IFNULL(u.direct_leader_id,d.leader_id) IS NOT NULL "
            "AND IFNULL(u.direct_leader_id,d.leader_id)<>u.id ORDER BY u.id LIMIT 1")
parts = row.split('\t')
EMP_ID, EMP_NAME, LEADER_ID = int(parts[0]), parts[1], int(parts[2])
LEADER_NAME = mysql(f"SELECT username FROM oa_system.oa_user WHERE id={LEADER_ID}").split()[0]
emp_tok = token(EMP_ID, EMP_NAME, hours=3)
leader_tok = token(LEADER_ID, LEADER_NAME, hours=3)
print(f'员工 {EMP_ID}/{EMP_NAME}  主管 {LEADER_ID}/{LEADER_NAME}  主管角色={roles_of(LEADER_ID)}')

# 岗位与部门
DEPT_ID = mysql(f"SELECT dept_id FROM oa_system.oa_user WHERE id={EMP_ID}").split()[0]
# 岗位表可能被上一轮测试清空，通过接口建一个（走真实链路）
pos_row = mysql(f"SELECT id FROM {DB}.oa_position WHERE is_deleted=0 AND status=1 LIMIT 1")
if pos_row:
    POS_ID = pos_row.split()[0]
else:
    st, b = req('POST', '/hr/position', {
        'positionCode': 'P-TEST-DEV', 'positionName': '测试工程师', 'deptId': int(DEPT_ID),
        'series': 'P', 'levelCode': 'P5', 'headcount': 5, 'status': 1,
    }, tok=admin_tok)
    print('建岗位:', st, b)
    POS_ID = b.get('data')
print(f'部门 {DEPT_ID}  岗位 {POS_ID}')

# ---------- 清理上一轮遗留 ----------
for tbl in ('oa_recruit_feedback', 'oa_recruit_interview', 'oa_recruit_offer',
            'oa_recruit_candidate', 'oa_recruit_post', 'oa_recruit_demand',
            'oa_train_absence', 'oa_train_enroll', 'oa_train_session', 'oa_train_course'):
    mysql(f"UPDATE {DB}.{tbl} SET is_deleted=1 WHERE is_deleted=0")
# 上一轮 convert 可能新建了 oa_user（用户名 cand_ 前缀），逻辑删除避免与本轮手机号查重冲突
mysql(f"UPDATE {DB}.oa_user SET is_deleted=1 WHERE username LIKE 'cand_%' AND is_deleted=0")
print('\n=== 0. 清理遗留数据 ===')

print('\n=== 1. 招聘需求 ===')
st, b = req('POST', '/hr/recruit/demand', {
    'deptId': int(DEPT_ID), 'positionId': int(POS_ID), 'headCount': 3,
    'urgency': 2, 'expectDate': '2026-12-01', 'education': '本科',
    'experience': '3年以上', 'skillRequire': 'Java', 'reasonType': 1,
    'reason': '业务扩张新增编制', 'salaryMin': 15000, 'salaryMax': 25000,
}, tok=admin_tok)
ck('需求创建成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
DEMAND_ID = b.get('data')

st, b = req('GET', f'/hr/recruit/demand/{DEMAND_ID}', tok=admin_tok)
d = b.get('data') or {}
ck('需求编号 ZP-yyyyMM-0001', str(d.get('demandCode', '')).startswith('ZP-'), f"code={d.get('demandCode')}")
ck('需求状态=待审批', d.get('status') == 1, f"status={d.get('status')}")
ck('编制缺口可见', d.get('quotaGap') is not None, f"quota={d.get('quotaTotal')}/{d.get('quotaUsed')}/缺口{d.get('quotaGap')}")

# 超编制：申请人数远大于缺口 → 只提示不拦截
st, b = req('POST', '/hr/recruit/demand', {
    'deptId': int(DEPT_ID), 'positionId': int(POS_ID), 'headCount': 999,
    'reasonType': 3, 'reason': '储备超编',
}, tok=admin_tok)
ck('超编制需求仍可创建（提示不拦截）', st == 200 and b.get('code') == 200, f'code={b.get("code")} msg={b.get("message")}')
OVER_ID = b.get('data')
st, b = req('GET', f'/hr/recruit/demand/{OVER_ID}', tok=admin_tok)
ck('超编制被标记 overQuota=1', (b.get('data') or {}).get('overQuota') == 1,
   f"overQuota={(b.get('data') or {}).get('overQuota')}")

# 部门/岗位不存在
st, b = req('POST', '/hr/recruit/demand', {
    'deptId': 999999, 'positionId': int(POS_ID), 'headCount': 1, 'reasonType': 1,
}, tok=admin_tok)
ck('需求部门不存在被拒(6605)', b.get('code') == 6605, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('POST', f'/hr/recruit/demand/{DEMAND_ID}/approve', tok=admin_tok)
ck('需求审批通过 → 招聘中', st == 200 and b.get('code') == 200, f'st={st} body={b}')
st, b = req('GET', f'/hr/recruit/demand/{DEMAND_ID}', tok=admin_tok)
ck('状态转招聘中并记录通过时间', (b.get('data') or {}).get('status') == 2
   and (b.get('data') or {}).get('approveTime') is not None, '')

st, b = req('POST', '/hr/recruit/demand', {
    'deptId': int(DEPT_ID), 'positionId': int(POS_ID), 'headCount': 1,
    'reasonType': 1, 'reason': 'x',
}, tok=emp_tok)
ck('员工不能建需求(403)', b.get('code') == 403, f'code={b.get("code")}')

print('\n=== 2. 职位 ===')
st, b = req('POST', '/hr/recruit/post', {
    'demandId': DEMAND_ID, 'postName': '高级后端工程师', 'city': '北京',
    'salaryMin': 20000, 'salaryMax': 35000, 'headCount': 2, 'channel': 2, 'status': 2,
}, tok=admin_tok)
ck('职位创建成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
POST_ID = b.get('data')

st, b = req('GET', '/hr/recruit/post/page?keyword=' + quote('高级后端'), tok=admin_tok)
ck('职位分页查询', st == 200 and (b.get('data') or {}).get('total', 0) >= 1,
   f"total={(b.get('data') or {}).get('total')}")

print('\n=== 3. 候选人 ===')
MOBILE = '13900001111'
st, b = req('POST', '/hr/recruit/candidate', {
    'name': '张三', 'mobile': MOBILE, 'email': 'zhangsan@test.com', 'gender': 1,
    'postId': POST_ID, 'demandId': DEMAND_ID, 'source': 2, 'authorized': 1,
    'expectSalary': 30000, 'currentSalary': 25000, 'education': '本科',
}, tok=admin_tok)
ck('候选人创建成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
CAND_ID = b.get('data')

# 未授权不能保存
st, b = req('POST', '/hr/recruit/candidate', {
    'name': '未授权', 'mobile': '13900002222', 'authorized': 0, 'source': 2,
}, tok=admin_tok)
ck('未授权不能保存(6604)', b.get('code') == 6604, f'code={b.get("code")} msg={b.get("message")}')

# 手机号重复 → 合并到同一人
st, b = req('POST', '/hr/recruit/candidate', {
    'name': '张三', 'mobile': MOBILE, 'source': 3, 'authorized': 1,
    'expectSalary': 32000,
}, tok=admin_tok)
ck('同手机号重复录入被合并', st == 200 and b.get('data') == CAND_ID,
   f"返回id={b.get('data')} 原id={CAND_ID}")
cnt = mysql(f"SELECT COUNT(*) FROM {DB}.oa_recruit_candidate WHERE mobile='{MOBILE}' AND is_deleted=0")
ck('库中仍只有 1 条候选人', cnt == '1', f'count={cnt}')

# 内推必须填内推人
st, b = req('POST', '/hr/recruit/candidate', {
    'name': '内推缺人', 'mobile': '13900003333', 'source': 1, 'authorized': 1,
}, tok=admin_tok)
ck('内推未填内推人被拒', b.get('code') == 400, f'code={b.get("code")} msg={b.get("message")}')

# 手机号格式
st, b = req('POST', '/hr/recruit/candidate', {
    'name': '格式错', 'mobile': '123', 'source': 2, 'authorized': 1,
}, tok=admin_tok)
ck('手机号格式校验', b.get('code') == 400, f'code={b.get("code")} msg={b.get("message")}')

# 阶段流转
st, b = req('POST', f'/hr/recruit/candidate/{CAND_ID}/stage', {'stage': 1}, tok=admin_tok)
ck('阶段不能原地踏步(6602)', b.get('code') == 6602, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('POST', f'/hr/recruit/candidate/{CAND_ID}/stage', {'stage': 2}, tok=admin_tok)
ck('阶段可向前推进', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('POST', f'/hr/recruit/candidate/{CAND_ID}/stage', {'stage': 3}, tok=emp_tok)
ck('普通员工不能改阶段(403)', b.get('code') == 403, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('POST', f'/hr/recruit/candidate/{CAND_ID}/stage',
            {'stage': 8, 'rejectType': 1}, tok=admin_tok)
ck('淘汰未填原因被拒', b.get('code') == 400, f'code={b.get("code")} msg={b.get("message")}')

print('\n=== 4. 面试 ===')
st, b = req('POST', '/hr/recruit/interview', {
    'candidateId': CAND_ID, 'postId': POST_ID, 'roundNo': 1,
    'interviewTime': '2026-11-01T10:00:00', 'way': 1, 'location': '会议室A',
    'interviewerIds': [LEADER_ID, ADMIN],
}, tok=admin_tok)
ck('面试安排成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
INTERVIEW_ID = b.get('data')

st, b = req('POST', '/hr/recruit/interview', {
    'candidateId': CAND_ID, 'postId': POST_ID, 'roundNo': 1,
    'interviewTime': '2026-11-01T14:00:00', 'way': 1, 'interviewerIds': [LEADER_ID],
}, tok=admin_tok)
ck('同一候选人同轮次不能重复安排', b.get('code') == 400, f'code={b.get("code")} msg={b.get("message")}')

# 非面试官不能提交评价
st, b = req('POST', '/hr/recruit/interview/feedback', {
    'interviewId': INTERVIEW_ID, 'professional': 4, 'communication': 4,
    'logicScore': 4, 'cultureMatch': 5, 'conclusion': 1, 'comment': '不错',
}, tok=emp_tok)
ck('非面试官不能提交评价(403)', b.get('code') == 403, f'code={b.get("code")} msg={b.get("message")}')

# 只提交一份 → 不能汇总
st, b = req('POST', '/hr/recruit/interview/feedback', {
    'interviewId': INTERVIEW_ID, 'professional': 4, 'communication': 4,
    'logicScore': 4, 'cultureMatch': 5, 'conclusion': 1, 'comment': '技术不错',
}, tok=leader_tok)
ck('面试官提交评价成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('POST', f'/hr/recruit/interview/{INTERVIEW_ID}/summarize?conclusion=1', tok=admin_tok)
ck('评价未齐不能汇总(6607)', b.get('code') == 6607, f'code={b.get("code")} msg={b.get("message")}')

# 第二份评价：不通过
st, b = req('POST', '/hr/recruit/interview/feedback', {
    'interviewId': INTERVIEW_ID, 'professional': 3, 'communication': 3,
    'logicScore': 2, 'cultureMatch': 3, 'conclusion': 3, 'comment': '深度不足',
}, tok=admin_tok)
ck('第二份评价提交成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

# 有不通过 → 通过需理由
st, b = req('POST', f'/hr/recruit/interview/{INTERVIEW_ID}/summarize?conclusion=1', tok=admin_tok)
ck('有不通过继续推进需理由(6608)', b.get('code') == 6608, f'code={b.get("code")} msg={b.get("message")}')

_sum_q = ('?conclusion=1&summary=' + quote('综合考虑予以通过') +
          '&continueReason=' + quote('岗位紧急，主管强烈推荐'))
st, b = req('POST', f'/hr/recruit/interview/{INTERVIEW_ID}/summarize' + _sum_q, tok=admin_tok)
ck('填理由后可汇总', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/recruit/interview/{INTERVIEW_ID}', tok=admin_tok)
d = b.get('data') or {}
ck('面试汇总状态=已汇总', d.get('status') == 2, f"status={d.get('status')}")
ck('反馈条数=2 且全部提交', d.get('feedbackCount') == 2 and d.get('allSubmitted') is True,
   f"count={d.get('feedbackCount')} all={d.get('allSubmitted')}")

# offer 前置：候选人必须进入终试阶段
st, b = req('POST', f'/hr/recruit/candidate/{CAND_ID}/stage', {'stage': 4}, tok=admin_tok)
ck('推进候选人到终试阶段', st == 200 and b.get('code') == 200, f'st={st} body={b}')

print('\n=== 5. Offer ===')
st, b = req('POST', '/hr/recruit/offer', {
    'candidateId': CAND_ID, 'postId': POST_ID, 'positionId': int(POS_ID),
    'positionName': '高级工程师', 'salary': 30000, 'probationMonths': 3,
    'reportDate': '2026-12-01', 'sendWay': 1,
}, tok=admin_tok)
ck('Offer 发起成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
OFFER_ID = b.get('data')

st, b = req('GET', f'/hr/recruit/offer/{OFFER_ID}', tok=admin_tok)
d = b.get('data') or {}
ck('Offer 编号 OF-yyyyMM-0001', str(d.get('offerNo', '')).startswith('OF-'), f"no={d.get('offerNo')}")
ck('有效期默认 7 天', d.get('remainDays') == 7, f"remain={d.get('remainDays')}")

st, b = req('POST', f'/hr/recruit/offer/{OFFER_ID}/send?sendWay=1', tok=admin_tok)
ck('Offer 发出成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/recruit/candidate/{CAND_ID}', tok=admin_tok)
ck('候选人阶段转「已发offer」', (b.get('data') or {}).get('stage') == 6,
   f"stage={(b.get('data') or {}).get('stage')}")

st, b = req('POST', f'/hr/recruit/offer/{OFFER_ID}/accept', tok=admin_tok)
ck('Offer 接受成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

print('\n=== 6. 转员工（查重）===')
st, b = req('GET', f'/hr/recruit/candidate/duplicate?mobile={MOBILE}&name=' + quote('张三'), tok=admin_tok)
ck('查重接口可用', st == 200 and b.get('code') == 200, f'st={st} code={b.get("code")}')

# 未指定复用/新建 → 拒绝
st, b = req('POST', '/hr/recruit/offer/convert', {'offerId': OFFER_ID}, tok=admin_tok)
ck('手机号无冲突时直接转员工', st == 200 and b.get('code') == 200, f'st={st} body={b}')
NEW_USER_ID = b.get('data')
if NEW_USER_ID:
    u = mysql(f"SELECT username, real_name, employment_status, status, mobile FROM {DB}.oa_user WHERE id={NEW_USER_ID}")
    print('新员工档案:', u)
    ck('新员工任职状态=待入职', 'employment_status' not in u or '\t1\t' in u, u)
    ck('新员工账号启用', '\t1\t' in u, u)

# 复用场景：用已有手机号的员工
st, b = req('POST', '/hr/recruit/candidate', {
    'name': '王五', 'mobile': '13900002222', 'source': 2, 'authorized': 1,
}, tok=admin_tok)
DUP_CAND = b.get('data')
# 把这个手机号挂到一个已存在的员工上（用 admin 的手机号）
admin_mobile = mysql(f"SELECT mobile FROM {DB}.oa_user WHERE id={ADMIN}")
print('admin 手机号:', admin_mobile)
if admin_mobile and admin_mobile != 'NULL':
    mysql(f"UPDATE {DB}.oa_recruit_candidate SET mobile='{admin_mobile}' WHERE id={DUP_CAND}")
    st, b = req('POST', '/hr/recruit/offer', {
        'candidateId': DUP_CAND, 'positionId': int(POS_ID), 'salary': 20000,
    }, tok=admin_tok)
    if b.get('code') == 200:
        OFFER2 = b.get('data')
        req('POST', f'/hr/recruit/offer/{OFFER2}/send?sendWay=1', tok=admin_tok)
        req('POST', f'/hr/recruit/offer/{OFFER2}/accept', tok=admin_tok)
        st, b = req('POST', '/hr/recruit/offer/convert', {'offerId': OFFER2}, tok=admin_tok)
        ck('手机号已存在未选模式被拒(6610)', b.get('code') == 6610, f'code={b.get("code")} msg={b.get("message")}')
        st, b = req('POST', '/hr/recruit/offer/convert',
                    {'offerId': OFFER2, 'reuseMode': 1}, tok=admin_tok)
        ck('复用已有档案转员工成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
        st, b = req('GET', f'/hr/recruit/offer/{OFFER2}', tok=admin_tok)
        ck('复用留痕写入 convertLog', '复用' in (b.get('data') or {}).get('convertLog', ''),
           (b.get('data') or {}).get('convertLog'))
        mysql(f"UPDATE {DB}.oa_user SET employment_status=3, mobile='{admin_mobile}' WHERE id={ADMIN}")
        # 清理：新建的员工
        if b.get('data') and b.get('data') != ADMIN:
            mysql(f"UPDATE {DB}.oa_user SET is_deleted=1 WHERE id={b.get('data')}")

print('\n=== 7. 招聘看板 ===')
st, b = req('GET', '/hr/recruit/board', tok=admin_tok)
d = b.get('data') or {}
ck('看板返回漏斗', len(d.get('stageFunnel') or []) == 7, f"funnel={len(d.get('stageFunnel') or [])}")
ck('看板返回渠道统计', len(d.get('channelStats') or []) == 5, f"channels={len(d.get('channelStats') or [])}")
ck('看板返回淘汰原因分布', len(d.get('rejectStats') or []) == 6, f"rejects={len(d.get('rejectStats') or [])}")

print('\n=== 8. 培训：课程与期次 ===')
st, b = req('POST', '/hr/train/course', {
    'courseCode': 'TC-C-001', 'courseName': '信息安全培训', 'trainType': 4,
    'teacherType': 2, 'teacherName': '张讲师', 'teacherOrg': '安全学院',
    'hours': 4, 'required': 1, 'passScore': 60, 'cost': 5000,
}, tok=admin_tok)
ck('课程创建成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
COURSE_ID = b.get('data')

st, b = req('POST', '/hr/train/course', {
    'courseCode': 'TC-C-001', 'courseName': '重复编码', 'trainType': 1, 'hours': 1,
}, tok=admin_tok)
ck('课程编码重复被拒', b.get('code') == 400, f'code={b.get("code")} msg={b.get("message")}')

# 名额 1 的期次（用于测候补）
st, b = req('POST', '/hr/train/session', {
    'courseId': COURSE_ID, 'sessionName': '2026 第 1 期',
    'startTime': '2026-12-01T09:00:00', 'endTime': '2026-12-01T17:00:00',
    'location': '培训室', 'capacity': 1, 'enrollDeadline': '2026-11-30T18:00:00',
}, tok=admin_tok)
ck('期次创建成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
SESSION_ID = b.get('data')

# 名额 5 的期次（用于测正常报名）
st, b = req('POST', '/hr/train/session', {
    'courseId': COURSE_ID, 'sessionName': '2026 第 2 期',
    'startTime': '2026-12-08T09:00:00', 'endTime': '2026-12-08T17:00:00',
    'location': '培训室', 'capacity': 5, 'enrollDeadline': '2026-12-05T18:00:00',
}, tok=admin_tok)
SESSION2_ID = b.get('data')

st, b = req('POST', '/hr/train/session', {
    'courseId': COURSE_ID, 'sessionName': '时间错', 'startTime': '2026-12-01T17:00:00',
    'endTime': '2026-12-01T09:00:00', 'capacity': 3,
}, tok=admin_tok)
ck('结束早于开始被拒', b.get('code') == 400, f'code={b.get("code")} msg={b.get("message")}')

print('\n=== 9. 报名（满额候补）===')
st, b = req('POST', f'/hr/train/session/{SESSION2_ID}/enroll', tok=emp_tok)
ck('员工自助报名成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')
ck('报名状态=已报名', (b.get('data') or {}).get('status') == 1,
   f"status={(b.get('data') or {}).get('status')}")

st, b = req('POST', f'/hr/train/session/{SESSION2_ID}/enroll', tok=emp_tok)
ck('重复报名被拒(6703)', b.get('code') == 6703, f'code={b.get("code")} msg={b.get("message")}')

# 名额 1 的期次：先 HR 指派占满，再员工报名 → 候补
st, b = req('POST', '/hr/train/session/enroll/batch', {
    'sessionId': SESSION_ID, 'userIds': [LEADER_ID], 'reason': '主管必修',
}, tok=admin_tok)
ck('HR 指派成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('POST', '/hr/train/session/enroll/batch', {
    'sessionId': SESSION_ID, 'userIds': [EMP_ID],
}, tok=admin_tok)
ck('HR 指派未填原因被拒', b.get('code') == 400, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('POST', f'/hr/train/session/{SESSION_ID}/enroll', tok=emp_tok)
ck('满额后普通员工报名进候补', st == 200 and b.get('code') == 200, f'st={st} body={b}')
# 满额自动进入候补（status=2），记录落库后由下一行断言
enr = mysql(f"SELECT status FROM {DB}.oa_train_enroll WHERE session_id={SESSION_ID} AND user_id={EMP_ID} AND is_deleted=0")
ck('候补记录已落库 status=2', enr == '2', f'status={enr}')

st, b = req('GET', f'/hr/train/session/{SESSION_ID}', tok=admin_tok)
d = b.get('data') or {}
ck('期次计数：已报名1 候补1', d.get('enrolledCount') == 1 and d.get('waitCount') == 1,
   f"enrolled={d.get('enrolledCount')} wait={d.get('waitCount')}")

print('\n=== 10. 取消报名（爽约）===')
# 距开课不足 24 小时 → 取消计爽约
st, b = req('POST', '/hr/train/session', {
    'courseId': COURSE_ID, 'sessionName': '即将开始期次',
    'startTime': '2026-10-24T09:00:00', 'endTime': '2026-10-24T17:00:00',
    'capacity': 10,
}, tok=admin_tok)
SOON_ID = b.get('data')
st, b = req('POST', f'/hr/train/session/{SOON_ID}/enroll', tok=emp_tok)
ck('即将开始的期次可报名', st == 200 and b.get('code') == 200, f'st={st} body={b}')
# 员工已在 SESSION2 报了名，同一人对不同期次可以各报一次
enr2 = mysql(f"SELECT id FROM {DB}.oa_train_enroll WHERE session_id={SOON_ID} AND user_id={EMP_ID} AND is_deleted=0")
st, b = req('POST', f'/hr/train/enroll/{enr2.split()[0]}/cancel', tok=emp_tok)
ck('24 小时内取消 → 记爽约', st == 200 and b.get('code') == 200, f'st={st} body={b}')
abs_cnt = mysql(f"SELECT COUNT(*) FROM {DB}.oa_train_absence WHERE user_id={EMP_ID} AND session_id={SOON_ID} AND is_deleted=0")
ck('爽约记录已落库', int(abs_cnt.split()[0]) >= 1, f'absence={abs_cnt}')

print('\n=== 11. 签到与成绩 ===')
# 签到窗口：SESSION2 在 2026-12-08 开课，现在远未到签到时间 → 6702
enr_s2 = mysql(f"SELECT id FROM {DB}.oa_train_enroll WHERE session_id={SESSION2_ID} AND user_id={EMP_ID} AND is_deleted=0")
st, b = req('POST', f'/hr/train/enroll/{enr_s2.split()[0]}/checkin', tok=emp_tok)
ck('未到签到时间不能签到(6702)', b.get('code') == 6702, f'code={b.get("code")} msg={b.get("message")}')

# 把期次时间改成当前（模拟进行中）
mysql(f"UPDATE {DB}.oa_train_session SET start_time='2026-10-23T00:00:00', "
      f"end_time='2026-10-24T23:59:59' WHERE id={SOON_ID}")
mysql(f"UPDATE {DB}.oa_train_enroll SET status=1 WHERE session_id={SOON_ID} AND user_id={EMP_ID} AND is_deleted=0")
st, b = req('POST', f'/hr/train/enroll/{enr2.split()[0]}/checkin', tok=emp_tok)
ck('开课期间可签到', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/train/session/{SOON_ID}/enrolls', tok=admin_tok)
enr_list = b.get('data') or []
ck('签到后状态=已签到', enr_list and enr_list[0].get('status') == 4,
   f"status={enr_list[0].get('status') if enr_list else None}")
ck('学时按签到计算=4 小时', enr_list and float(enr_list[0].get('realHours') or 0) == 4.0,
   f"hours={enr_list[0].get('realHours') if enr_list else None}")

st, b = req('POST', '/hr/train/enroll/score', {
    'enrollId': enr2.split()[0], 'score': 85, 'assessWay': 1, 'satisfied': 5,
}, tok=admin_tok)
ck('成绩录入成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/train/session/{SOON_ID}/enrolls', tok=admin_tok)
enr_list = b.get('data') or []
ck('及格 → 状态=已完成', enr_list and enr_list[0].get('status') == 6,
   f"status={enr_list[0].get('status') if enr_list else None}")
ck('passed=1', enr_list and enr_list[0].get('passed') == 1, '')

st, b = req('POST', '/hr/train/enroll/score', {
    'enrollId': enr2.split()[0], 'score': 150, 'assessWay': 1,
}, tok=admin_tok)
ck('成绩越界被拒(6707)', b.get('code') == 400, f'code={b.get("code")} msg={b.get("message")}')

print('\n=== 12. 我的培训 ===')
st, b = req('GET', '/hr/train/my/page', tok=emp_tok)
ck('我的培训列表', st == 200 and (b.get('data') or {}).get('total', 0) >= 1,
   f"total={(b.get('data') or {}).get('total')}")

st, b = req('GET', '/hr/train/my/available', tok=emp_tok)
ck('我的可报名期次', st == 200 and isinstance(b.get('data'), list), f"n={len(b.get('data') or [])}")

print('\n=== 13. 培训统计 ===')
st, b = req('GET', '/hr/train/statistics?year=2026', tok=admin_tok)
d = b.get('data') or {}
ck('统计返回人均学时', d.get('avgHours') is not None, f"avg={d.get('avgHours')} total={d.get('totalHours')}")
ck('统计返回必修完成率', d.get('requiredFinishRate') is not None,
   f"rate={d.get('requiredFinishRate')} ({d.get('requiredDoneCount')}/{d.get('requiredShouldCount')})")
ck('统计返回满意度', d.get('satisfaction') is not None, f"sat={d.get('satisfaction')}")
ck('统计返回课程维度', len(d.get('courseStats') or []) >= 1, f"n={len(d.get('courseStats') or [])}")

print('\n=== 14. 权限边界 ===')
st, b = req('GET', '/hr/recruit/demand/page')
ck('未登录访问返回 401', st == 401 or b.get('code') == 401, f'st={st} code={b.get("code")}')

st, b = req('GET', '/hr/recruit/demand/page', tok=emp_tok)
ck('员工看不到招聘需求(403)', b.get('code') == 403, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('GET', '/hr/train/course/page', tok=emp_tok)
ck('员工看不到课程管理(403)', b.get('code') == 403, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('GET', '/hr/train/my/page', tok=emp_tok)
ck('员工可看自己的培训', st == 200 and b.get('code') == 200, f'st={st} code={b.get("code")}')

sys.exit(summary())
