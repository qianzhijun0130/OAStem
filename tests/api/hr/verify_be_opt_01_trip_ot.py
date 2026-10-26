#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""补验：出差/加班回调重算 + 薪资核算页「折算系数 / 折算后标准工资」两列。"""
import sys, time

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
import xlib as X

DB = X.DB
M = "2026-10"
TRIP_D, OT_D = "2026-10-22", "2026-10-23"
U, UNAME = 101809, "tech_staff1"
TAG = "rv-trip-ot"


def q(sql):
    return X.mysql(sql)


def find_task(instance_id):
    proc = q(f"SELECT IFNULL(proc_inst_id,'') FROM {DB}.oa_approval_instance WHERE id={instance_id}").strip()
    if not proc:
        return None
    return q(f"SELECT IFNULL(ID_,'') FROM {DB}.act_ru_task WHERE PROC_INST_ID_='{proc}' LIMIT 1").strip() or None


def approve_all(admin, instance_id):
    body = {"code": None}
    for _ in range(3):
        tid = find_task(instance_id)
        if not tid:
            break
        _, body = X.req("POST", "/workflow/approval/complete",
                        {"taskId": tid, "action": "approve", "opinion": TAG}, tok=admin)
        if body.get("code") != 200:
            break
        time.sleep(0.3)
    return body


print("== 0. 清理与造数 ==")
q(f"DELETE FROM {DB}.oa_leave WHERE reason LIKE '{TAG}%'")
q(f"DELETE FROM {DB}.oa_business_trip WHERE reason LIKE '{TAG}%'")
q(f"DELETE FROM {DB}.oa_overtime WHERE reason LIKE '{TAG}%'")
q(f"DELETE FROM {DB}.oa_attendance WHERE attendance_date BETWEEN '{TRIP_D}' AND '{OT_D}'")
q(f"DELETE FROM {DB}.oa_attendance_month WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_shift_schedule WHERE schedule_date BETWEEN '{M}-01' AND '{M}-31'")
q(f"DELETE FROM {DB}.oa_shift WHERE shift_code='RVTEST'")
q(f"DELETE FROM {DB}.oa_salary_profile WHERE user_id={U}")

adm = X.token(1, "admin")
usr = X.token(U, UNAME)

st, b = X.req("POST", "/hr/attendance/shift", {
    "shiftCode": "RVTEST", "shiftName": "TripOtVerify", "workStart": "09:00:00",
    "workEnd": "18:00:00", "restMinutes": 60, "lateGrace": 5, "earlyGrace": 0}, tok=adm)
shift_id = b.get("data")
X.ck("建班次", b.get("code") == 200 and shift_id, f"id={shift_id}")
st, b = X.req("POST", "/hr/attendance/schedule/batch",
              {"deptId": 2, "month": M, "shiftId": shift_id, "overwrite": 1}, tok=adm)
X.ck("批量排班", b.get("code") == 200 and (b.get("data") or 0) > 0, f"count={b.get('data')}")
st, b = X.req("POST", f"/hr/attendance/settle?date={TRIP_D}", tok=adm)
st, b = X.req("POST", f"/hr/attendance/settle?date={OT_D}", tok=adm)
st1 = q(f"SELECT attendance_status FROM {DB}.oa_attendance WHERE user_id={U} AND attendance_date='{TRIP_D}'")
st2 = q(f"SELECT attendance_status FROM {DB}.oa_attendance WHERE user_id={U} AND attendance_date='{OT_D}'")
X.ck("两天先被判为缺勤(4)", st1 == "4" and st2 == "4", f"trip={st1} ot={st2}")

print("== 1. 出差审批 → 回调重算，缺勤转出差(6) ==")
st, b = X.req("POST", "/hr/request", {
    "bizType": 2, "destination": "Shenzhen", "startDate": TRIP_D, "endDate": TRIP_D,
    "budget": 500, "reason": TAG + "-trip"}, tok=usr)
trip_id = b.get("data")
X.ck("提交出差成功", b.get("code") == 200 and trip_id, f"code={b.get('code')} msg={(b.get('message') or '')[:40]}")
if trip_id:
    inst = q(f"SELECT IFNULL(instance_id,0) FROM {DB}.oa_business_trip WHERE id={trip_id}").strip()
    body = approve_all(adm, int(inst)) if inst and inst != "0" else {}
    X.ck("出差审批通过", body.get("code") == 200, f"code={body.get('code')}")
    time.sleep(1.5)
    s = q(f"SELECT attendance_status,settled FROM {DB}.oa_attendance WHERE user_id={U} AND attendance_date='{TRIP_D}'")
    p = s.split("\t") if s else []
    X.ck("出差当天考勤变为出差(6)", bool(p) and p[0] == "6", f"row={s}")
    X.ck("出差当天已重新结算", bool(p) and p[1] == "1", f"row={s}")

print("== 2. 加班审批 → 回调重算（不改变出勤性质），月结计入加班时数 ==")
st, b = X.req("POST", "/hr/request", {
    "bizType": 1, "overtimeType": 1, "overtimeDate": OT_D, "hours": 3, "compensateType": 2,
    "reason": TAG + "-ot", "startTime": f"{OT_D}T19:00:00", "endTime": f"{OT_D}T22:00:00"}, tok=usr)
ot_id = b.get("data")
X.ck("提交加班成功", b.get("code") == 200 and ot_id, f"code={b.get('code')} msg={(b.get('message') or '')[:40]}")
if ot_id:
    inst = q(f"SELECT IFNULL(instance_id,0) FROM {DB}.oa_overtime WHERE id={ot_id}").strip()
    body = approve_all(adm, int(inst)) if inst and inst != "0" else {}
    X.ck("加班审批通过", body.get("code") == 200, f"code={body.get('code')}")
    time.sleep(1.5)
    s = q(f"SELECT attendance_status FROM {DB}.oa_attendance WHERE user_id={U} AND attendance_date='{OT_D}'")
    X.ck("加班不改变出勤性质（仍为缺勤 4）", s == "4", f"status={s}")

st, b = X.req("POST", f"/hr/attendance/rebuild?month={M}", tok=adm)
X.ck("重算月度汇总", b.get("code") == 200, f"code={b.get('code')}")
mj = q(f"SELECT overtime_json,leave_days_json,trip_days FROM {DB}.oa_attendance_month WHERE user_id={U} AND month='{M}'")
X.ck("月结计入加班 3 小时", "工作日" in mj and "3" in mj, f"json={mj[:70]}")
X.ck("月结计入出差 1 天", mj.split("\t")[2] == "1.0" if mj else False, f"trip_days={mj.split(chr(9))[2] if mj else ''}")

print("== 3. 核算页两列：折算系数 / 折算后标准工资 ==")
q(f"INSERT INTO {DB}.oa_salary_profile (user_id,effective_date,city,base_salary,post_salary,perf_salary,allowance) "
  f"VALUES ({U},'2026-01-01','默认',8000,1000,1000,0)")
q(f"UPDATE {DB}.oa_user SET entry_date='{M}-08' WHERE id={U}")
# 对照组：101810 改为整月在职，用于断言「未折算 = 1.0」
q(f"UPDATE {DB}.oa_user SET entry_date='2025-01-01' WHERE id=101810")
dept_users = [int(x) for x in q(
    f"SELECT id FROM {DB}.oa_user WHERE is_deleted=0 AND dept_id=2 AND employment_status IN (2,3,4)").split()]
for uid in dept_users:
    if uid == U:
        continue
    q(f"INSERT IGNORE INTO {DB}.oa_salary_profile (user_id,effective_date,city,base_salary,post_salary,perf_salary,allowance) "
      f"VALUES ({uid},'2026-01-01','默认',8000,1000,1000,0)")
for uid in dept_users:
    q(f"DELETE FROM {DB}.oa_attendance_month WHERE user_id={uid} AND month='{M}'")
    q(f"INSERT INTO {DB}.oa_attendance_month (user_id,month,should_days,actual_days,absent_days,"
      f"late_count,early_count,missing_count,trip_days,status) VALUES ({uid},'{M}',22,22,0,0,0,0,0,2)")
q(f"DELETE FROM {DB}.oa_salary_record WHERE month='{M}'")
st, b = X.req("POST", f"/hr/salary/calc?month={M}&deptId=2", tok=adm)
X.ck("核算成功", b.get("code") == 200, f"code={b.get('code')} msg={(b.get('message') or '')[:50]}")

st, b = X.req("GET", f"/hr/salary/records?month={M}&pageSize=100", tok=adm)
rows = (b.get("data") or {}).get("records", [])
me = next((r for r in rows if r.get("userId") == U), None)
X.ck("列表返回 prorationFactor / proratedStandard 字段", me is not None
     and me.get("prorationFactor") is not None and me.get("proratedStandard") is not None,
     str({k: me.get(k) for k in ("prorationFactor", "proratedStandard")}) if me else "none")
if me:
    f = float(me["prorationFactor"])
    ps = float(me["proratedStandard"])
    X.ck("折算系数在 (0,1) 区间", 0 < f < 1, f"factor={f}")
    X.ck("折算后标准工资 = 10000 × 系数", abs(ps - round(10000 * f, 2)) < 0.02,
         f"prorated={ps} 期望={round(10000 * f, 2)}")
    other = next((r for r in rows if r.get("userId") == 101810), None)
    if other:
        X.ck("未折算员工系数为 1", float(other["prorationFactor"] or 0) == 1.0,
             f"factor={other.get('prorationFactor')}")

print("== 4. 还原 ==")
q(f"DELETE FROM {DB}.oa_salary_record WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_attendance_month WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_salary_profile WHERE user_id IN ({','.join(str(x) for x in dept_users)})")
q(f"UPDATE {DB}.oa_user SET entry_date='2026-01-01' WHERE id={U}")
q(f"UPDATE {DB}.oa_user SET entry_date='2026-10-04' WHERE id=101810")
q(f"DELETE FROM {DB}.oa_business_trip WHERE reason LIKE '{TAG}%'")
q(f"DELETE FROM {DB}.oa_overtime WHERE reason LIKE '{TAG}%'")
q(f"DELETE FROM {DB}.oa_attendance WHERE attendance_date BETWEEN '{TRIP_D}' AND '{OT_D}'")
q(f"DELETE FROM {DB}.oa_shift_schedule WHERE schedule_date BETWEEN '{M}-01' AND '{M}-31'")
q(f"DELETE FROM {DB}.oa_shift WHERE shift_code='RVTEST'")
X.ck("测试数据已清理",
     q(f"SELECT COUNT(*) FROM {DB}.oa_shift WHERE shift_code='RVTEST'") == "0"
     and q(f"SELECT entry_date FROM {DB}.oa_user WHERE id={U}").strip() == "2026-01-01")

sys.exit(X.summary())
