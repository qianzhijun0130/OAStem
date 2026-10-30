#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BE-OPT-02 + BE-OPT-01 + BIZ-OPT-04 验证（v2：补齐造数与权限场景）。"""
import sys, datetime

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
import xlib as X

DB = X.DB
M = "2026-10"


def q(sql):
    return X.mysql(sql)


print("== 0. 造数 ==")
# 个税断言精确编码「5000 × 任职月数」，成立前提是「该员工本年没有更早的已复核记录」。
# 而 hr_salary.js 等 UI 脚本会造上个月的数据，累计预扣会把它们算进来 → taxable 多出几千。
# 前置状态由本用例自己建立，不依赖「碰巧库里没有」，否则跑的顺序一变就红（本轮就是这样红的）。
X.mysql_exec(f"DELETE FROM {DB}.oa_salary_record_item WHERE record_id IN "
             f"(SELECT id FROM {DB}.oa_salary_record WHERE user_id IN (101808,101809) "
             f"AND month LIKE '2026-%' AND month < '{M}' AND status >= 2)")
X.mysql_exec(f"DELETE FROM {DB}.oa_salary_record WHERE user_id IN (101808,101809) "
             f"AND month LIKE '2026-%' AND month < '{M}' AND status >= 2")
q(f"DELETE FROM {DB}.oa_salary_record WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_attendance_month WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_salary_profile WHERE user_id IN (101808,101809,101810)")
q(f"DELETE FROM {DB}.oa_social_policy WHERE policy_year=2026 AND city='默认'")
q(f"INSERT INTO {DB}.oa_social_policy (city,policy_year,base_min,base_max,fund_base_min,fund_base_max) "
  f"VALUES ('默认',2026,2000,30000,2000,30000)")
for uid in (101808, 101809, 101810):
    q(f"INSERT INTO {DB}.oa_salary_profile (user_id,effective_date,city,base_salary,post_salary,perf_salary,allowance) "
      f"VALUES ({uid},'2026-01-01','默认',8000,1000,1000,0)")
# 一条跨部门记录，用于证明数据范围真的生效
q(f"INSERT INTO {DB}.oa_salary_record (user_id,month,gross_pay,net_pay,status) "
  f"VALUES (101811,'{M}',20000,20000,1)")

adm = X.token(1, "admin")
hr = X.token(101811, "hr_staff1")

print("== 1. 薪资列表数据范围（BE-OPT-02 第 1 处） ==")
st, b = X.req("GET", f"/hr/salary/records?month={M}&pageSize=100", tok=adm)
ids = {r.get("userId") for r in (b.get("data") or {}).get("records", [])}
X.ck("超管(范围=全部)可见跨部门记录", ids == {101811}, f"ids={ids}")

# 把 HR 角色临时改为「本部门」，验证 @DataScope 真的收窄（HR 在部门 3）
q(f"UPDATE {DB}.oa_role SET data_scope=2 WHERE role_code='HR'")
st, b = X.req("GET", f"/hr/salary/records?month={M}&pageSize=100", tok=hr)
ids_scope2 = {r.get("userId") for r in (b.get("data") or {}).get("records", [])}
q(f"UPDATE {DB}.oa_role SET data_scope=1 WHERE role_code='HR'")
X.ck("范围=本部门时只看到本部门(101811)", ids_scope2 == {101811}, f"ids={ids_scope2}")
# 范围=全部但数据在别的部门 → 全部可见（证明是范围在起作用，不是数据本身为空）
q(f"DELETE FROM {DB}.oa_salary_record WHERE month='{M}'; "
  f"INSERT INTO {DB}.oa_salary_record (user_id,month,gross_pay,net_pay,status) VALUES (101809,'{M}',10000,10000,1)")
st, b = X.req("GET", f"/hr/salary/records?month={M}&pageSize=100", tok=hr)
ids_other = {r.get("userId") for r in (b.get("data") or {}).get("records", [])}
X.ck("范围=全部时能看到其他部门(技术部 101809)", ids_other == {101809}, f"ids={ids_other}")

print("== 2. 假勤导出（BE-OPT-02 第 2 处） ==")
st, raw = X.req("GET", f"/hr/leave/export?month={M}", tok=adm, raw=True)
X.ck("导出返回 xlsx（PK 魔数）", st == 200 and raw[:2] == b"PK", f"st={st} size={len(raw) if raw else 0}")
st, b = X.req("GET", f"/hr/leave/export?month={M}", tok=X.token(101808, "tech_admin"))
X.ck("无 hr:leave:export 权限被拒(403)", isinstance(b, dict) and b.get("code") == 403,
     f"code={b.get('code') if isinstance(b, dict) else 'binary'}")

print("== 3. 无考勤月结阻断（BIZ-OPT-04 第 3 项） ==")
st, b = X.req("POST", f"/hr/salary/calc?month={M}&deptId=2", tok=adm)
X.ck("缺考勤月结时核算被阻断且点名", b.get("code") != 200 and "考勤汇总" in (b.get("message") or ""),
     f"code={b.get('code')} msg={(b.get('message') or '')[:50]}")

print("== 4. 折算与个税月数（BIZ-OPT-04 第 1/2 项） ==")
for uid in (101808, 101809, 101810):
    q(f"INSERT INTO {DB}.oa_attendance_month (user_id,month,should_days,actual_days,absent_days,"
      f"late_count,early_count,missing_count,trip_days,status) VALUES ({uid},'{M}',22,22,0,0,0,0,0,2)")
st, b = X.req("POST", f"/hr/salary/calc?month={M}&deptId=2", tok=adm)
X.ck("考勤齐备后核算成功", b.get("code") == 200, f"code={b.get('code')} msg={(b.get('message') or '')[:50]}")

row = q(f"SELECT gross_pay,base_salary,post_salary,perf_salary,allowance,remark,cumulative_taxable,"
        f"social_personal,fund_personal FROM {DB}.oa_salary_record WHERE month='{M}' AND user_id=101808")
if row:
    p = row.split("\t")
    gross = float(p[0]); standard = float(p[1]) + float(p[2]) + float(p[3]) + float(p[4])
    remark = p[5]; taxable = float(p[6])
    special = float(p[7]) + float(p[8])
    total_wd = sum(1 for d in range(1, 32) if datetime.date(2026, 10, d).weekday() < 5)
    part_wd = sum(1 for d in range(4, 32) if datetime.date(2026, 10, d).weekday() < 5)
    expected = round(min(part_wd, total_wd) / total_wd, 4)
    X.ck("入职当月按在职天数折算（gross < 标准）", gross < standard - 1, f"gross={gross} standard={standard}")
    X.ck("折算系数与手算一致", abs(gross / standard - expected) < 0.002,
         f"actual={round(gross / standard, 4)} expected={expected}")
    X.ck("备注写明折算依据", "折算系数" in remark, remark[:50])
    X.ck("个税减除按任职月数(1 个月, 5000)",
         abs(taxable - (gross - 5000 * 1 - special)) < 1.0,
         f"taxable={taxable} 期望={round(gross - 5000 - special, 2)}")
    # 对照：2026-01 入职的人当月应按满月（10 个月任职，减除 50000）
    row2 = q(f"SELECT gross_pay,cumulative_taxable,remark,social_personal,fund_personal "
             f"FROM {DB}.oa_salary_record WHERE month='{M}' AND user_id=101809")
    if row2:
        p2 = row2.split("\t")
        X.ck("老员工不折算（gross=标准工资）", abs(float(p2[0]) - 10000) < 1, f"gross={p2[0]}")
        s2 = float(p2[3]) + float(p2[4])
        X.ck("老员工个税减除按 10 个月(50000)",
             abs(float(p2[1]) - (float(p2[0]) - 50000 - s2)) < 1.0,
             f"taxable={p2[1]} 期望={round(float(p2[0]) - 50000 - s2, 2)}")
else:
    print("FAIL | 未生成 101808 记录 | ")

print("== 5. 数据还原 ==")
q(f"DELETE FROM {DB}.oa_salary_record WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_attendance_month WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_salary_profile WHERE user_id IN (101808,101809,101810)")
X.ck("测试数据已清理",
     q(f"SELECT COUNT(*) FROM {DB}.oa_salary_record WHERE month='{M}'") == "0")

sys.exit(X.summary())
