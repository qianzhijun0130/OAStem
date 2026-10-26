#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BE-OPT-01 重算链路补验：日结判缺勤 → 请假审批通过 → 回调重算 → 月结不再计旷工。

全程用接口造数（班次/排班/请假），避免 mysql -e 写中文的静默失败陷阱。
"""
import sys, time

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
import xlib as X

DB = X.DB
M, D = "2026-10", "2026-10-20"
U, UNAME = 101809, "tech_staff1"
REASON = "resettle-verify"


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
                        {"taskId": tid, "action": "approve", "opinion": "resettle-verify"}, tok=admin)
        if body.get("code") != 200:
            break
        time.sleep(0.3)
    return body


print("== 0. 清理并造数（全部走接口） ==")
q(f"DELETE FROM {DB}.oa_leave WHERE reason='{REASON}'")
q(f"DELETE FROM {DB}.oa_attendance WHERE attendance_date='{D}'")
q(f"DELETE FROM {DB}.oa_attendance_month WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_shift_schedule WHERE schedule_date BETWEEN '{M}-01' AND '{M}-31'")
q(f"DELETE FROM {DB}.oa_shift WHERE shift_code='TRESET'")

adm = X.token(1, "admin")
usr = X.token(U, UNAME)

st, b = X.req("POST", "/hr/attendance/shift", {
    "shiftCode": "TRESET", "shiftName": "ResettleVerify", "workStart": "09:00:00",
    "workEnd": "18:00:00", "restMinutes": 60, "lateGrace": 5, "earlyGrace": 0}, tok=adm)
shift_id = b.get("data")
X.ck("建测试班次成功", b.get("code") == 200 and shift_id, f"code={b.get('code')} id={shift_id}")

st, b = X.req("POST", "/hr/attendance/schedule/batch",
              {"deptId": 2, "month": M, "shiftId": shift_id, "overwrite": 1}, tok=adm)
X.ck("批量排班成功", b.get("code") == 200 and (b.get("data") or 0) > 0, f"count={b.get('data')}")

print("== 1. 日结：无打卡被判定缺勤（status=4） ==")
st, b = X.req("POST", f"/hr/attendance/settle?date={D}", tok=adm)
X.ck("日结执行成功", b.get("code") == 200, f"code={b.get('code')} msg={(b.get('message') or '')[:40]}")
status = q(f"SELECT attendance_status FROM {DB}.oa_attendance WHERE user_id={U} AND attendance_date='{D}'")
X.ck("当天先被判为缺勤(4)", status == "4", f"status={status}")

print("== 2. 提交请假并审批通过（触发回调重算） ==")
st, b = X.req("POST", "/hr/leave", {"leaveType": 1, "startTime": f"{D}T09:00:00",
                                    "endTime": f"{D}T18:00:00", "leaveDays": 1, "reason": REASON}, tok=usr)
leave_id = b.get("data")
X.ck("提交请假成功", b.get("code") == 200 and leave_id, f"code={b.get('code')} msg={(b.get('message') or '')[:40]}")
inst = q(f"SELECT IFNULL(instance_id,0) FROM {DB}.oa_leave WHERE id={leave_id}").strip() if leave_id else "0"
if inst and inst != "0":
    body = approve_all(adm, int(inst))
    X.ck("审批通过", body.get("code") == 200, f"code={body.get('code')} msg={(body.get('message') or '')[:40]}")
    time.sleep(1.5)   # 重算走 afterCommit，稍等落地

print("== 3. 回调后考勤被重算为请假（status=5） ==")
row = q(f"SELECT attendance_status,settled FROM {DB}.oa_attendance WHERE user_id={U} AND attendance_date='{D}'")
p = row.split("\t") if row else []
X.ck("考勤状态由缺勤改为请假(5)", bool(p) and p[0] == "5", f"row={row}")
X.ck("考勤已重新结算(settled=1)", bool(p) and p[1] == "1", f"row={row}")

print("== 4. 月结互斥：请假当天不再计旷工 ==")
st, b = X.req("POST", f"/hr/attendance/rebuild?month={M}", tok=adm)
X.ck("重算月度汇总成功", b.get("code") == 200, f"code={b.get('code')}")
mrow = q(f"SELECT absent_days,leave_days_json,should_days FROM {DB}.oa_attendance_month "
         f"WHERE user_id={U} AND month='{M}'")
mp = mrow.split("\t") if mrow else []
# 整月都排了班且无任何打卡：除请假那天外，其余应出勤日都应计旷工
X.ck("请假当天被排除在旷工之外（旷工 = 应出勤 - 1）",
     bool(mp) and abs(float(mp[0]) - (float(mp[2]) - 1)) < 0.001,
     f"absent={mp[0] if mp else 'none'} should={mp[2] if mp else 'none'}")
X.ck("月汇总计入事假 1 天", bool(mp) and "事假" in mp[1], f"leave={mp[1][:60] if mp else 'none'}")

print("== 5. 封账月保护：重算不静默跳过 ==")
q(f"UPDATE {DB}.oa_attendance_month SET status=2 WHERE month='{M}'")
st, b = X.req("POST", "/hr/leave", {"leaveType": 1, "startTime": f"{M}-21T09:00:00",
                                    "endTime": f"{M}-21T18:00:00", "leaveDays": 1,
                                    "reason": REASON + "-2"}, tok=usr)
leave2 = b.get("data")
if b.get("code") == 200 and leave2:
    inst2 = q(f"SELECT IFNULL(instance_id,0) FROM {DB}.oa_leave WHERE id={leave2}").strip()
    body2 = approve_all(adm, int(inst2)) if inst2 and inst2 != "0" else {}
    X.ck("封账月审批仍可完成（重算失败不回滚审批）", body2.get("code") == 200,
         f"code={body2.get('code')}")
    # 该日期应仍是缺勤/未改，说明重算被拦而非静默写入
    st21 = q(f"SELECT IFNULL(attendance_status,'none') FROM {DB}.oa_attendance WHERE user_id={U} AND attendance_date='{M}-21'")
    X.ck("封账月重算未生效（保持原状）", st21 in ("none", "4"), f"status={st21}")
else:
    print("SKIP | 第二张请假单未提交成功 | " + str(b.get("message"))[:60])

print("== 6. 还原 ==")
q(f"UPDATE {DB}.oa_attendance_month SET status=1 WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_leave WHERE reason LIKE '{REASON}%'")
q(f"DELETE FROM {DB}.oa_attendance WHERE attendance_date IN ('{D}','{M}-21')")
q(f"DELETE FROM {DB}.oa_attendance_month WHERE month='{M}'")
q(f"DELETE FROM {DB}.oa_shift_schedule WHERE schedule_date BETWEEN '{M}-01' AND '{M}-31'")
q(f"DELETE FROM {DB}.oa_shift WHERE shift_code='TRESET'")
X.ck("测试数据已清理",
     q(f"SELECT COUNT(*) FROM {DB}.oa_attendance WHERE attendance_date='{D}'") == "0"
     and q(f"SELECT COUNT(*) FROM {DB}.oa_shift WHERE shift_code='TRESET'") == "0")

sys.exit(X.summary())
