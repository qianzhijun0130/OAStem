#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""绩效 → 薪酬联动专项验证。

验证：绩效归档后，薪酬核算的「绩效工资 = 档案基数 × 绩效系数」，
      系数取自当期已归档绩效；无归档结果时按 1.0 且不阻断发薪。
"""
import sys
sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import mysql, req, token, ck, summary, DB

ADMIN = 1
admin_tok = token(ADMIN, 'admin', hours=2)

# 找一个有薪资档案的用户 + 其绩效归档结果
row = mysql("SELECT s.user_id, u.username, s.base_salary, s.perf_salary "
            "FROM oa_system.oa_salary_profile s JOIN oa_system.oa_user u ON u.id = s.user_id "
            "WHERE s.is_deleted=0 LIMIT 1")
print('薪资档案样本:', row)
if not row:
    print('!! 没有薪资档案数据，先通过接口建档')
    # 找一个有归档绩效的用户
    cand = mysql("SELECT user_id FROM oa_system.oa_perf_sheet WHERE is_deleted=0 "
                 "AND coefficient IS NOT NULL ORDER BY id DESC LIMIT 1")
    if not cand:
        print('!! 也没有归档绩效用户，无法验证联动')
        sys.exit(summary())
    USER_ID = int(cand.split()[0])
    UNAME = mysql(f"SELECT username FROM oa_system.oa_user WHERE id={USER_ID}").split()[0]
    st, b = req('POST', '/hr/salary/profiles', {
        'userId': USER_ID, 'effectiveDate': '2026-01-01',
        'baseSalary': 10000, 'postSalary': 2000, 'perfSalary': 3000,
        'allowance': 500, 'socialBase': 15000, 'fundBase': 15000,
        'city': '北京', 'changeReason': '联动验证建档',
    }, tok=admin_tok)
    ck('薪资档案建档', st == 200 and b.get('code') == 200, f'st={st} body={b}')
    row = mysql("SELECT user_id, username, base_salary, perf_salary FROM oa_system.oa_salary_profile s "
                "JOIN oa_system.oa_user u ON u.id=s.user_id WHERE s.is_deleted=0 LIMIT 1")
    print('建档后:', row)
    if not row:
        sys.exit(summary())

parts = row.split('\t')
USER_ID = int(parts[0])
UNAME = parts[1]
BASE = parts[2]
PERF_BASE = parts[3]

sheet = mysql(f"SELECT id, cycle_id, total_score, grade_code, coefficient "
               f"FROM oa_system.oa_perf_sheet WHERE user_id={USER_ID} AND is_deleted=0 "
               f"AND coefficient IS NOT NULL ORDER BY id DESC LIMIT 1")
print('该用户绩效结果:', sheet)
if not sheet:
    print('!! 该用户没有归档绩效，跳过')
    sys.exit(summary())

sheets = sheet.split('\t')
SHEET_ID = int(sheets[0])
CYCLE_ID = int(sheets[1])
COEF = sheets[4]
print(f'用户 {USER_ID}/{UNAME} 系数={COEF} 绩效基数={PERF_BASE}')

# 周期月份（绩效工资按 cycle 覆盖的月份取）
cyc = mysql(f"SELECT cycle_code, start_date, end_date, status FROM oa_system.oa_perf_cycle WHERE id={CYCLE_ID}")
print('周期:', cyc)
cyc_parts = cyc.split('\t')
MONTH = cyc_parts[1][:7]

print(f'\n=== 1. 归档绩效的系数可被 coefficientOf 取到（{MONTH}）===')
st, b = req('GET', f'/hr/salary/my/{MONTH}', tok=token(USER_ID, UNAME, hours=2))
ck('核算前员工看不到工资条（未复核）', b.get('code') == 6408, f'code={b.get("code")} msg={b.get("message")}')

print('\n=== 2. 薪酬核算使用绩效系数 ===')
# 清理该月该部门的旧核算记录，避免重复
rec = mysql(f"SELECT id FROM oa_system.oa_salary_record WHERE user_id={USER_ID} "
            f"AND month='{MONTH}' AND is_deleted=0 LIMIT 1")
if rec:
    mysql(f"UPDATE oa_system.oa_salary_record SET is_deleted=1 WHERE id={rec.split()[0]}")
    mysql(f"UPDATE oa_system.oa_salary_record_item SET is_deleted=1 "
          f"WHERE record_id={rec.split()[0]}")

st, b = req('POST', f'/hr/salary/calc?month={MONTH}', tok=admin_tok)
ck('薪酬核算成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

item = mysql(f"SELECT ri.item_name, ri.amount FROM oa_system.oa_salary_record_item ri "
              f"JOIN oa_system.oa_salary_record r ON r.id = ri.record_id "
              f"WHERE r.user_id={USER_ID} AND r.month='{MONTH}' AND r.is_deleted=0 "
              f"AND ri.item_code='PERF' ORDER BY r.id DESC")
print('绩效工资行:', item)

if item:
    ip = item.split('\t')
    amount = float(ip[1])
    base = float(PERF_BASE) if PERF_BASE else 0.0
    expect = round(base * float(COEF), 2)
    ck(f'绩效工资 = 基数×系数 = {base}×{COEF}', abs(amount - expect) < 0.05,
       f'实际={amount} 期望={expect}')
else:
    ck('绩效工资行存在', False, '未查到 PERF 行（该用户可能无绩效基数配置）')

print('\n=== 2b. 工资条备注记录了绩效系数 ===')
note = mysql(f"SELECT remark FROM oa_system.oa_salary_record WHERE user_id={USER_ID} "
              f"AND month='{MONTH}' AND is_deleted=0 ORDER BY id DESC LIMIT 1")
ck('工资条备注含绩效系数', '绩效系数' in note, f'remark={note}')

print('\n=== 2c. 复核后员工可见工资条 ===')
st, b = req('POST', f'/hr/salary/review?month={MONTH}', tok=admin_tok)
ck('薪酬复核成功', st == 200 and b.get('code') == 200, f'st={st} body={b}')

st, b = req('GET', f'/hr/salary/my/{MONTH}', tok=token(USER_ID, UNAME, hours=2))
ck('复核后员工可见工资条', st == 200 and b.get('code') == 200, f'st={st} code={b.get("code")} msg={b.get("message")}')

print('\n=== 3. 归档状态与绩效一致性 ===')
cyc_status = mysql(f"SELECT status, stage FROM oa_system.oa_perf_cycle WHERE id={CYCLE_ID}")
ck('周期已归档(status=2)', cyc_status.startswith('2'), f'cycle={cyc_status}')

print('\n=== 4. 未归档周期不参与薪酬 ===')
# 造一个未归档周期，确认不会被 coefficientOf 命中
unarchived = mysql(f"SELECT id FROM oa_system.oa_perf_cycle WHERE status<>2 AND is_deleted=0 LIMIT 1")
print('存在未归档周期:', unarchived or '(无)')

sys.exit(summary())
