#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P1-2 绩效模块接口验证。

覆盖：指标库 → 方案（权重校验/等级映射）→ 周期（重叠校验/自动生成考核表）
     → 目标提交 → 上级审核 → 自评 → 上级评分 → 校准 → 确认 → 申诉 → 归档
     → 薪酬绩效系数联动。
"""
import sys
sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import mysql, req, token, ck, summary, perms_of, roles_of, DB
from urllib.parse import quote

BASE_SQL = "F:/GitHub/OAStem/oa-system/src/main/resources"
ADMIN = 1

# 需要一个真实员工 + 其上级（直属上级为空时取部门负责人，与后端 resolveLeader 同口径）
EMP_ROW = mysql("SELECT u.id, u.username, u.dept_id, "
                 "IFNULL(u.direct_leader_id, d.leader_id) AS leader_id "
                 "FROM oa_system.oa_user u "
                 "LEFT JOIN oa_system.oa_department d ON d.id = u.dept_id "
                 "WHERE u.is_deleted=0 AND u.status=1 AND u.employment_status IN (2,3,4) "
                 "AND IFNULL(u.direct_leader_id, d.leader_id) IS NOT NULL "
                 "AND IFNULL(u.direct_leader_id, d.leader_id) <> u.id "
                 "ORDER BY u.id LIMIT 1")
print('候选员工:', EMP_ROW)

parts = EMP_ROW.split('\t')
EMP_ID = int(parts[0])
EMP_NAME = parts[1]
DEPT_ID = int(parts[2])
LEADER_ID = int(parts[3])
LEADER_NAME = mysql(f"SELECT username FROM oa_system.oa_user WHERE id={LEADER_ID}").split('\t')[0]

print(f'员工 {EMP_ID}/{EMP_NAME}  上级 {LEADER_ID}/{LEADER_NAME}  部门 {DEPT_ID}')

# ---------- 令牌 ----------
admin_tok = token(ADMIN, 'admin', hours=2)
emp_tok = token(EMP_ID, EMP_NAME, hours=2)
leader_tok = token(LEADER_ID, LEADER_NAME, hours=2)
print('emp roles =', roles_of(EMP_ID), ' leader roles =', roles_of(LEADER_ID))
# 注意：上级审核目标/上级评分走 isAuthenticated() + 服务层 leaderId 校验，不需要权限码；
# 只有「考核表列表 / 校准 / 导出」这类 HR 动作才需要 hr:performance:* 权限码。
print('leader 是否 HR 角色 =', set(roles_of(LEADER_ID)) & {'SUPER_ADMIN', 'HR', 'HR_MANAGER'})

print('\n=== 0. 清理上一轮遗留（保证脚本可重复运行）===')
# 逻辑删绩效相关业务数据（保留逻辑删语义，不物理删）
for tbl in ('oa_perf_sheet_item', 'oa_perf_sheet', 'oa_perf_calibrate_log',
            'oa_perf_appeal', 'oa_perf_cycle', 'oa_perf_plan_grade',
            'oa_perf_plan_indicator', 'oa_perf_plan', 'oa_perf_indicator'):
    mysql(f"UPDATE {DB}.{tbl} SET is_deleted=1 WHERE is_deleted=0")

# 测试用员工入职日期为 2026-10-04，参与「入职满 1 个月」的考核需要更早，
# 这里临时前移（测完恢复），不改基线数据。
_orig_entry = mysql(f"SELECT entry_date FROM {DB}.oa_user WHERE id={EMP_ID}")
_orig_entry_leader = mysql(f"SELECT entry_date FROM {DB}.oa_user WHERE id={LEADER_ID}")
mysql(f"UPDATE {DB}.oa_user SET entry_date='2025-01-01' WHERE id={EMP_ID}")
mysql(f"UPDATE {DB}.oa_user SET entry_date='2025-01-01' WHERE id={LEADER_ID}")
print(f'临时调整 {EMP_NAME}/{LEADER_NAME} 入职日期 -> 2025-01-01（测完恢复）')

print('\n=== 1. 指标库 ===')
st, b = req('POST', '/hr/perf/indicator', {
    'indicatorCode': 'V-TEST-001', 'indicatorName': '测试指标-业绩',
    'indicatorType': 1, 'unit': '%', 'definition': '测试用',
    'scoreStandard': '90分以上优秀', 'status': 1,
}, tok=admin_tok)
ck('指标创建 201/200', st == 200 and b.get('code') == 200, f'st={st} body={b}')
IND_ID = b.get('data')

st, b = req('POST', '/hr/perf/indicator', {
    'indicatorCode': 'V-TEST-001', 'indicatorName': '重复指标',
    'indicatorType': 1, 'status': 1,
}, tok=admin_tok)
ck('指标编码重复被拒(6502)', b.get('code') == 6502, f'code={b.get("code")} msg={b.get("message")}')

# 加分项 + 扣分项
st, b = req('POST', '/hr/perf/indicator', {
    'indicatorCode': 'V-TEST-BONUS', 'indicatorName': '测试加分项',
    'indicatorType': 3, 'status': 1,
}, tok=admin_tok)
BONUS_ID = b.get('data')
st, b = req('POST', '/hr/perf/indicator', {
    'indicatorCode': 'V-TEST-PEN', 'indicatorName': '测试扣分项',
    'indicatorType': 4, 'status': 1,
}, tok=admin_tok)
PEN_ID = b.get('data')
ck('加分/扣分指标创建', bool(BONUS_ID) and bool(PEN_ID), f'bonus={BONUS_ID} pen={PEN_ID}')

st, b = req('GET', '/hr/perf/indicator/page?keyword=V-TEST', tok=admin_tok)
ck('指标分页查询', st == 200 and b.get('code') == 200 and len((b.get('data') or {}).get('records') or []) >= 3,
   f"total={(b.get('data') or {}).get('total')}")

print('\n=== 2. 绩效方案 ===')
st, b = req('POST', '/hr/perf/plan', {
    'planCode': 'V-PLAN-001', 'planName': '验证用绩效方案', 'cycleType': 2, 'scoreMode': 1,
    'status': 2, 'distributionLimit': 0.2,
    'grades': [
        {'gradeCode': 'A', 'gradeName': '优秀', 'minScore': 90, 'maxScore': 120, 'coefficient': 1.20, 'sort': 1},
        {'gradeCode': 'B', 'gradeName': '良好', 'minScore': 80, 'maxScore': 89.9, 'coefficient': 1.00, 'sort': 2},
        {'gradeCode': 'C', 'gradeName': '合格', 'minScore': 70, 'maxScore': 79.9, 'coefficient': 0.80, 'sort': 3},
        {'gradeCode': 'D', 'gradeName': '不合格', 'minScore': 0, 'maxScore': 69.9, 'coefficient': 0.60, 'sort': 4},
    ],
    'indicators': [
        {'indicatorId': IND_ID, 'weight': 100, 'sort': 1},
        {'indicatorId': BONUS_ID, 'weight': 0, 'sort': 2},
        {'indicatorId': PEN_ID, 'weight': 0, 'sort': 3},
    ],
}, tok=admin_tok)
ck('方案创建', st == 200 and b.get('code') == 200, f'st={st} body={b}')
PLAN_ID = b.get('data')

st, b = req('POST', '/hr/perf/plan', {
    'planCode': 'V-PLAN-BAD', 'planName': '权重不合法方案', 'status': 1,
    'indicators': [{'indicatorId': IND_ID, 'weight': 50}],
}, tok=admin_tok)
ck('权重合计≠100 被拒(6501)', b.get('code') == 6501, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('GET', f'/hr/perf/plan/{PLAN_ID}', tok=admin_tok)
d = b.get('data') or {}
ck('方案详情含等级与指标', len(d.get('grades') or []) == 4 and len(d.get('indicators') or []) == 3,
   f"grades={len(d.get('grades') or [])} indicators={len(d.get('indicators') or [])}")

st, b = req('GET', '/hr/perf/plan/options', tok=admin_tok)
ck('方案下拉只含启用方案', any(x.get('id') == PLAN_ID for x in (b.get('data') or [])),
   f"n={len(b.get('data') or [])}")

print('\n=== 3. 周期 ===')
# 周期起始要足够早：员工 2026-10-04 入职，「入职满 1 个月才参与考核」会把它排除
st, b = req('POST', '/hr/perf/cycle', {
    'cycleCode': 'V-CYC-001', 'cycleName': '验证周期', 'planId': PLAN_ID,
    'startDate': '2026-01-01', 'endDate': '2026-03-31',
    'targetDeadline': '2026-01-10', 'selfDeadline': '2026-02-10',
    'scoreDeadline': '2026-02-20', 'calibrateDeadline': '2026-03-05',
    'confirmDeadline': '2026-03-10',
}, tok=admin_tok)
ck('周期创建并自动生成考核表', st == 200 and b.get('code') == 200, f'st={st} body={b}')
CYCLE_ID = b.get('data')

st, b = req('POST', '/hr/perf/cycle', {
    'cycleCode': 'V-CYC-002', 'cycleName': '重叠周期', 'planId': PLAN_ID,
    'startDate': '2026-02-01', 'endDate': '2026-03-31',
}, tok=admin_tok)
ck('同方案日期重叠被拒(6513)', b.get('code') == 6513, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('GET', f'/hr/perf/cycle/{CYCLE_ID}', tok=admin_tok)
d = b.get('data') or {}
ck('周期详情阶段=目标设定且已生成考核表', d.get('stage') == 1 and d.get('sheetCount', 0) > 0,
   f"stage={d.get('stage')} sheets={d.get('sheetCount')}")

print('\n=== 4. 考核表 / 目标 ===')
st, b = req('GET', f'/hr/perf/sheet/my?cycleId={CYCLE_ID}', tok=emp_tok)
ck('员工查自己考核表', st == 200 and b.get('code') == 200, f'st={st} body={b}')
SHEET = b.get('data') or {}
SHEET_ID = SHEET.get('id')
ITEMS = SHEET.get('items') or []
ck('考核表含指标行', len(ITEMS) == 3, f"items={len(ITEMS)}")

# 权重快照：60/0/0
ck('指标行快照了名称与权重', all(i.get('indicatorName') and i.get('weight') is not None for i in ITEMS),
   f"w={[i.get('weight') for i in ITEMS]}")

# 目标提交前先测「未填目标」
st, b = req('POST', '/hr/perf/target/my', {'sheetId': SHEET_ID, 'items': []}, tok=emp_tok)
ck('未填目标提交被拒(6517)', b.get('code') == 6517, f'code={b.get("code")} msg={b.get("message")}')

goals = [{'itemId': i['id'], 'targetValue': '100%', 'measureStandard': '达成年度目标'} for i in ITEMS]
st, b = req('POST', '/hr/perf/target/my', {'sheetId': SHEET_ID, 'items': goals}, tok=emp_tok)
ck('目标提交成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/perf/sheet/my?cycleId={CYCLE_ID}', tok=emp_tok)
ck('目标状态=待审核', (b.get('data') or {}).get('targetStatus') == 1,
   f"targetStatus={(b.get('data') or {}).get('targetStatus')}")

# 越权：员工自己审核目标
st, b = req('POST', f'/hr/perf/target/{SHEET_ID}/approve?pass=true', tok=emp_tok)
ck('员工不能自审目标(6507)', b.get('code') == 6507, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('POST', f'/hr/perf/target/{SHEET_ID}/approve?pass=true&opinion=' + quote('同意'), tok=leader_tok)
ck('上级审核目标通过', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/perf/sheet/my?cycleId={CYCLE_ID}', tok=emp_tok)
ck('目标已锁定', (b.get('data') or {}).get('targetStatus') == 2,
   f"targetStatus={(b.get('data') or {}).get('targetStatus')}")

print('\n=== 5. 自评 ===')
st, b = req('POST', '/hr/perf/score/self', {'sheetId': SHEET_ID, 'items': []}, tok=emp_tok)
ck('空阶段提交自评被拒', b.get('code') != 200, f'code={b.get("code")} msg={b.get("message")}')

# 推进到自评阶段
st, b = req('POST', f'/hr/perf/cycle/{CYCLE_ID}/advance', tok=admin_tok)
ck('周期推进到自评阶段', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/perf/cycle/{CYCLE_ID}', tok=admin_tok)
ck('阶段=自评中', (b.get('data') or {}).get('stage') == 2, f"stage={(b.get('data') or {}).get('stage')}")

# 分数越界
bad = [{'itemId': ITEMS[0]['id'], 'score': 150}]
st, b = req('POST', '/hr/perf/score/self', {'sheetId': SHEET_ID, 'items': bad}, tok=emp_tok)
ck('分数越界被拒(6508)', b.get('code') == 6508, f'code={b.get("code")} msg={b.get("message")}')

# 正常自评：主指标 90 分 × 100% = 90；加分 5；扣分 3 → 总分 92
scores = [
    {'itemId': ITEMS[0]['id'], 'score': 90, 'comment': '基本达成'},
    {'itemId': ITEMS[1]['id'], 'score': 5},
    {'itemId': ITEMS[2]['id'], 'score': 3},
]
st, b = req('POST', '/hr/perf/score/self', {'sheetId': SHEET_ID, 'items': scores}, tok=emp_tok)
ck('自评提交成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/perf/sheet/my?cycleId={CYCLE_ID}', tok=emp_tok)
d = b.get('data') or {}
ck('自评总分=92.0', str(d.get('selfTotal')) in ('92.0', '92'), f"selfTotal={d.get('selfTotal')}")

print('\n=== 6. 上级评分 ===')
# 先推进到「上级评分」阶段（阶段机：1 目标设定 → 2 自评中 → 3 上级评分）
st, b = req('POST', f'/hr/perf/cycle/{CYCLE_ID}/advance', tok=admin_tok)
ck('周期推进到上级评分阶段', st == 200 and b.get('code') == 200, f'st={st} body={b}')
st, b = req('GET', f'/hr/perf/cycle/{CYCLE_ID}', tok=admin_tok)
ck('阶段=上级评分', (b.get('data') or {}).get('stage') == 3, f"stage={(b.get('data') or {}).get('stage')}")

st, b = req('POST', '/hr/perf/score/manager', {'sheetId': SHEET_ID, 'items': scores, 'comment': '总体良好'}, tok=emp_tok)
ck('员工不能给自己上级评分(6507)', b.get('code') == 6507, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('POST', '/hr/perf/score/manager', {'sheetId': SHEET_ID, 'items': scores}, tok=leader_tok)
ck('上级评分必须填总评语', b.get('code') != 200, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('POST', '/hr/perf/score/manager', {'sheetId': SHEET_ID, 'items': scores, 'comment': '总体良好，继续努力'}, tok=leader_tok)
ck('上级评分成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/perf/sheet/my?cycleId={CYCLE_ID}', tok=emp_tok)
d = b.get('data') or {}
ck('上级总分=92.0 等级=A', str(d.get('managerTotal')) in ('92.0', '92') and d.get('gradeCode') == 'A',
   f"managerTotal={d.get('managerTotal')} grade={d.get('gradeCode')}")
ck('系数=1.20', str(d.get('coefficient')) in ('1.20', '1.2'), f"coefficient={d.get('coefficient')}")

print('\n=== 7. 校准 ===')
st, b = req('POST', '/hr/perf/score/calibrate', {'sheetId': SHEET_ID, 'score': 95}, tok=admin_tok)
ck('校准未填理由被拒(6509)', b.get('code') == 6509, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('POST', '/hr/perf/score/calibrate',
            {'sheetId': SHEET_ID, 'score': 95, 'reason': '项目贡献突出，上调一档'}, tok=admin_tok)
ck('校准成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/perf/sheet/my?cycleId={CYCLE_ID}', tok=emp_tok)
d = b.get('data') or {}
ck('校准后总分=95 等级=A 系数=1.2',
   str(d.get('totalScore')) in ('95.0', '95') and d.get('gradeCode') == 'A'
   and str(d.get('coefficient')) in ('1.20', '1.2'),
   f"total={d.get('totalScore')} grade={d.get('gradeCode')} coef={d.get('coefficient')}")

cal_log = mysql(f"SELECT COUNT(*) FROM oa_system.oa_perf_calibrate_log WHERE sheet_id={SHEET_ID}")
ck('校准留痕记录', cal_log == '1', f'log={cal_log}')

print('\n=== 8. 申诉 ===')
st, b = req('POST', '/hr/perf/appeal', {'sheetId': SHEET_ID, 'reason': '评分偏低'}, tok=emp_tok)
ck('未确认不能申诉（阶段不符）', b.get('code') != 200, f'code={b.get("code")} msg={b.get("message")}')

# 推进到结果确认阶段（已在上级评分阶段，再推进 2 次：校准 → 结果确认）
for _ in range(2):
    st, b = req('POST', f'/hr/perf/cycle/{CYCLE_ID}/advance', tok=admin_tok)
    print('   advance ->', b.get('code'))
st, b = req('GET', f'/hr/perf/cycle/{CYCLE_ID}', tok=admin_tok)
ck('阶段=结果确认', (b.get('data') or {}).get('stage') == 5, f"stage={(b.get('data') or {}).get('stage')}")

st, b = req('POST', f'/hr/perf/score/{SHEET_ID}/confirm', tok=emp_tok)
ck('员工确认结果', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('POST', '/hr/perf/appeal', {'sheetId': SHEET_ID, 'reason': '评分偏低，申请复核'}, tok=emp_tok)
ck('员工提交申诉', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('POST', '/hr/perf/appeal', {'sheetId': SHEET_ID, 'reason': '再申诉一次'}, tok=emp_tok)
ck('重复申诉被拒(6510)', b.get('code') == 6510, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('GET', '/hr/perf/appeal/pending', tok=admin_tok)
ck('待处理申诉列表', len(b.get('data') or []) >= 1, f"n={len(b.get('data') or [])}")

APPEAL_ID = (b.get('data') or [{}])[0].get('id') if b.get('data') else None
st, b = req('POST', '/hr/perf/appeal/handle',
            {'appealId': APPEAL_ID, 'status': 3, 'opinion': '复核后调整为 95', 'score': 95}, tok=admin_tok)
ck('处理申诉并调整分数', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/perf/sheet/my?cycleId={CYCLE_ID}', tok=emp_tok)
d = b.get('data') or {}
ck('申诉调整后总分=95', str(d.get('totalScore')) in ('95.0', '95'), f"total={d.get('totalScore')}")

print('\n=== 9. 归档 ===')
st, b = req('POST', f'/hr/perf/cycle/{CYCLE_ID}/archive', tok=admin_tok)
ck('归档成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/perf/cycle/{CYCLE_ID}', tok=admin_tok)
ck('归档后阶段=6 状态=2', (b.get('data') or {}).get('stage') == 6 and (b.get('data') or {}).get('status') == 2,
   f"stage={(b.get('data') or {}).get('stage')} status={(b.get('data') or {}).get('status')}")

st, b = req('POST', '/hr/perf/target/my', {'sheetId': SHEET_ID, 'items': goals}, tok=emp_tok)
ck('归档后写操作被拒(6512)', b.get('code') == 6512, f'code={b.get("code")} msg={b.get("message")}')

print('\n=== 10. 分布 / 导出 / 列表 ===')
st, b = req('GET', f'/hr/perf/distribution?cycleId={CYCLE_ID}', tok=admin_tok)
ck('等级分布统计', st == 200 and b.get('code') == 200 and isinstance(b.get('data'), list),
   f"n={len(b.get('data') or [])}")

st, b = req('GET', f'/hr/perf/export?cycleId={CYCLE_ID}', tok=admin_tok, raw=True)
ck('导出 Excel', st == 200 and isinstance(b, bytes) and b[:2] == b'PK',
   f'st={st} bytes={len(b) if isinstance(b, bytes) else 0}')

st, b = req('GET', '/hr/perf/sheet/page?cycleId=%d' % CYCLE_ID, tok=admin_tok)
ck('考核表分页(HR)', st == 200 and (b.get('data') or {}).get('total', 0) > 0,
   f"total={(b.get('data') or {}).get('total')}")

st, b = req('GET', '/hr/perf/my/page', tok=emp_tok)
ck('我的绩效历史', st == 200 and (b.get('data') or {}).get('total', 0) >= 1,
   f"total={(b.get('data') or {}).get('total')}")

st, b = req('GET', '/hr/perf/team/page?cycleId=%d' % CYCLE_ID, tok=leader_tok)
ck('上级看团队考核表', st == 200 and b.get('code') == 200, f'st={st} code={b.get("code")} total={(b.get("data") or {}).get("total")}')

print('\n=== 11. 权限边界 ===')
st, b = req('GET', '/hr/perf/plan/page', tok=emp_tok)
ck('员工看不到方案列表(403)', b.get('code') == 403, f'code={b.get("code")} msg={b.get("message")}')

st, b = req('GET', '/hr/perf/plan/page')
ck('未登录访问返回 401', st == 401 or b.get('code') == 401, f'st={st} code={b.get("code")}')

print('\n=== 12. 薪酬绩效系数联动 ===')
st, b = req('GET', f'/hr/perf/sheet/{SHEET_ID}', tok=emp_tok)
ck('本人可查看考核表明细', st == 200 and b.get('code') == 200, f'st={st} code={b.get("code")}')

# 恢复入职日期
if _orig_entry:
    mysql(f"UPDATE {DB}.oa_user SET entry_date='{_orig_entry}' WHERE id={EMP_ID}")
if _orig_entry_leader:
    mysql(f"UPDATE {DB}.oa_user SET entry_date='{_orig_entry_leader}' WHERE id={LEADER_ID}")
    print(f'已恢复 {EMP_NAME}/{LEADER_NAME} 入职日期')

sys.exit(summary())
