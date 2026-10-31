#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""人力资源 P0（岗位 / 员工档案 / 考勤）接口验证。

覆盖：
- P0-1 岗位 CRUD、编码唯一、有在岗员工不可删、在岗人数与超编标记
- P0-1 员工花名册、档案详情（敏感字段脱敏 / 原文）、任职状态机与账号联动、合同区间重叠、续签次数、证照
- P0-2 班次、批量排班、打卡（含重复打卡 / 未排班）、日结幂等、月结与封账不覆盖、解封

收尾会把测试期间改动过的数据全部还原（含被禁用账号）。
"""
import json
import sys
from datetime import date
from urllib.parse import quote

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, ck, mysql, mysql_exec, req, summary, token  # noqa: E402

DEPT_TECH = 2
ADMIN_ID = 1
TECH_STAFF1 = 101809
TECH_STAFF2 = 101810
TODAY = date.today().isoformat()
MONTH = TODAY[:7]
STATE = {}


def main():
    admin = token(ADMIN_ID, "admin", hours=2)
    tech = token(TECH_STAFF1, "tech_staff1", hours=2)

    # ============ P0-1 岗位 ============
    code, body = req("POST", "/hr/position", {
        "positionCode": "HR_TEST_DEV", "positionName": "测试工程师",
        "deptId": DEPT_TECH, "series": "P", "headcount": 1, "sort": 99,
    }, tok=admin)
    pid = (body.get("data") or {})
    ck("新增岗位成功", body.get("code") == 200 and pid, f"code={body.get('code')} msg={body.get('message')} pid={pid}")
    STATE["positionId"] = pid

    code, body = req("POST", "/hr/position", {
        "positionCode": "HR_TEST_DEV", "positionName": "重复编码", "deptId": DEPT_TECH,
    }, tok=admin)
    ck("岗位编码重复 → 6101", body.get("code") == 6101, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("GET", "/hr/position/page?keyword=HR_TEST_DEV&pageNo=1&pageSize=10", tok=admin)
    rows = ((body.get("data") or {}).get("records")) or []
    ck("岗位列表回填部门名称", rows and rows[0].get("deptName") == "技术部",
       f"deptName={rows[0].get('deptName') if rows else None}")
    ck("新岗位在岗人数为 0、未超编",
       rows and rows[0].get("onDutyCount") == 0 and rows[0].get("overHeadcount") is False,
       f"onDuty={rows[0].get('onDutyCount') if rows else None}")

    # ============ P0-1 花名册与档案 ============
    code, body = req("GET", "/hr/employee/page?pageNo=1&pageSize=50", tok=admin)
    data = body.get("data") or {}
    ck("花名册默认返回在职员工", data.get("total", 0) > 0, f"total={data.get('total')}")
    first = (data.get("records") or [{}])[0]
    ck("花名册手机号脱敏", "****" in (first.get("mobile") or ""), f"mobile={first.get('mobile')}")
    ck("花名册含司龄字段", bool(first.get("seniority")), f"seniority={first.get('seniority')}")

    code, body = req("GET", f"/hr/employee/{TECH_STAFF1}", tok=admin)
    detail = body.get("data") or {}
    ck("HR 看档案可见敏感原文", detail.get("canViewSensitive") is True and "****" not in (detail.get("mobile") or ""),
       f"canView={detail.get('canViewSensitive')} mobile={detail.get('mobile')}")
    ck("档案带出任职状态标签", detail.get("employmentStatusLabel") == "正式", str(detail.get("employmentStatusLabel")))

    code, body = req("GET", f"/hr/employee/{ADMIN_ID}", tok=tech)
    ck("员工看他人档案 → 403", body.get("code") == 403, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("GET", f"/hr/employee/{TECH_STAFF1}", tok=tech)
    ck("员工看自己档案 → 200 且脱敏", body.get("code") == 200
       and (body.get("data") or {}).get("canViewSensitive") is False,
       f"code={body.get('code')}")

    # 岗位 + 直属上级
    code, body = req("POST", f"/hr/employee/{TECH_STAFF1}/profile", {
        "positionId": pid, "directLeaderId": 101808,
    }, tok=admin)
    ck("更新档案（岗位/上级）成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("GET", f"/hr/employee/{TECH_STAFF1}", tok=admin)
    detail = body.get("data") or {}
    ck("岗位名称同步到档案", detail.get("positionName") == "测试工程师", str(detail.get("positionName")))
    ck("直属上级名称回填", detail.get("directLeaderName") == "张明", str(detail.get("directLeaderName")))

    code, body = req("GET", "/hr/position/page?keyword=HR_TEST_DEV&pageNo=1&pageSize=10", tok=admin)
    rows = ((body.get("data") or {}).get("records")) or []
    ck("在岗人数随档案更新为 1", rows and rows[0].get("onDutyCount") == 1,
       f"onDuty={rows[0].get('onDutyCount') if rows else None}")
    ck("在岗 1 / 编制 1 未超编", rows and rows[0].get("overHeadcount") is False, "")

    # 任职状态机
    code, body = req("POST", f"/hr/employee/{TECH_STAFF1}/profile", {
        "employmentStatus": 4, "changeRemark": "已提交离职申请",
    }, tok=admin)
    ck("正式 → 待离职 合法", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("POST", f"/hr/employee/{TECH_STAFF1}/profile", {
        "employmentStatus": 3, "changeRemark": "撤回离职",
    }, tok=admin)
    ck("待离职 → 正式 合法（可撤销离职）", body.get("code") == 200, f"code={body.get('code')}")

    code, body = req("POST", f"/hr/employee/{TECH_STAFF2}/profile", {
        "employmentStatus": 1, "changeRemark": "非法跳转测试",
    }, tok=admin)
    ck("正式 → 待入职 被拒（6103）", body.get("code") == 6103, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("POST", f"/hr/employee/{TECH_STAFF1}/profile", {
        "employmentStatus": 4,
    }, tok=admin)
    ck("变更任职状态未填说明被拒", body.get("code") == 400, f"code={body.get('code')} msg={body.get('message')}")

    # 离职联动禁用账号（用 tech_staff2 测，收尾 DB 还原）
    code, body = req("POST", f"/hr/employee/{TECH_STAFF2}/profile", {
        "employmentStatus": 5, "changeRemark": "验证离职联动禁用账号",
    }, tok=admin)
    ck("正式 → 已离职 合法", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")
    row = mysql(f"SELECT CONCAT(employment_status,'/',status) FROM oa_system.oa_user WHERE id={TECH_STAFF2}").strip()
    ck("离职后账号被自动禁用（status=0）", row == "5/0", f"employment/status={row}")

    # ============ 合同 ============
    code, body = req("POST", f"/hr/employee/{TECH_STAFF1}/contract", {
        "contractNo": "HT-TEST-001", "contractType": 1,
        "startDate": "2025-01-01", "endDate": "2027-12-31", "signDate": "2025-01-01",
    }, tok=admin)
    contract_id = body.get("data")
    ck("新增合同成功", body.get("code") == 200 and contract_id, f"code={body.get('code')} msg={body.get('message')}")
    STATE["contractId"] = contract_id

    code, body = req("POST", f"/hr/employee/{TECH_STAFF1}/contract", {
        "contractNo": "HT-TEST-OVERLAP", "contractType": 1,
        "startDate": "2027-01-01", "endDate": "2028-12-31",
    }, tok=admin)
    ck("合同区间重叠 → 6104", body.get("code") == 6104, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("POST", f"/hr/employee/{TECH_STAFF1}/contract", {
        "contractNo": "HT-TEST-002", "contractType": 1, "renew": True,
        "startDate": "2028-01-01", "endDate": "2030-12-31",
    }, tok=admin)
    renew_id = body.get("data")
    ck("续签合同成功", body.get("code") == 200, f"code={body.get('code')}")
    STATE["renewContractId"] = renew_id

    code, body = req("GET", f"/hr/employee/{TECH_STAFF1}", tok=admin)
    contracts = (body.get("data") or {}).get("contracts") or []
    counts = sorted([c.get("signCount") for c in contracts])
    ck("续签后签订次数递增为 2", counts == [1, 2], f"signCounts={counts}")
    ck("合同类型标签正确", contracts and contracts[0].get("contractTypeLabel") == "固定期限",
       str(contracts[0].get("contractTypeLabel") if contracts else None))
    ck("合同到期天数已计算", contracts and contracts[0].get("daysToExpire") is not None,
       str(contracts[0].get("daysToExpire") if contracts else None))

    code, body = req("DELETE", f"/hr/employee/{TECH_STAFF1}/contract/{renew_id}", tok=admin)
    ck("删除续签合同成功", body.get("code") == 200, f"code={body.get('code')}")

    # ============ 证照 ============
    code, body = req("POST", f"/hr/employee/{TECH_STAFF1}/cert", {
        "certType": 1, "certName": "本科毕业证", "issueOrg": "示例大学", "issueDate": "2018-06-30",
    }, tok=admin)
    cert_id = body.get("data")
    ck("新增证照成功", body.get("code") == 200 and cert_id, f"code={body.get('code')} msg={body.get('message')}")
    STATE["certId"] = cert_id

    code, body = req("GET", f"/hr/employee/{TECH_STAFF1}", tok=admin)
    certs = (body.get("data") or {}).get("certs") or []
    ck("证照列表带类型标签", certs and certs[0].get("certTypeLabel") == "学历",
       str(certs[0].get("certTypeLabel") if certs else None))

    # 有在岗员工时删除岗位应被拒
    code, body = req("DELETE", f"/hr/position/{pid}", tok=admin)
    ck("岗位下有在职员工不可删 → 6102", body.get("code") == 6102, f"code={body.get('code')} msg={body.get('message')}")

    # ============ P0-2 班次与排班 ============
    code, body = req("POST", "/hr/attendance/shift", {
        "shiftCode": "HR_DAY", "shiftName": "测试常白班", "workStart": "09:00:00", "workEnd": "18:00:00",
        "restMinutes": 60, "lateGrace": 5, "earlyGrace": 0,
    }, tok=admin)
    shift_id = body.get("data")
    ck("新增班次成功", body.get("code") == 200 and shift_id, f"code={body.get('code')} msg={body.get('message')}")
    STATE["shiftId"] = shift_id

    code, body = req("POST", "/hr/attendance/shift", {
        "shiftCode": "HR_DAY", "shiftName": "重复编码", "workStart": "09:00:00", "workEnd": "18:00:00",
    }, tok=admin)
    ck("班次编码重复被拒", body.get("code") == 400, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("GET", "/hr/attendance/shifts", tok=admin)
    shifts = [s for s in (body.get("data") or []) if s.get("shiftCode") == "HR_DAY"]
    ck("班次列表标准工时 8.00", shifts and shifts[0].get("standardHours") == "8.00",
       str(shifts[0].get("standardHours") if shifts else None))

    code, body = req("POST", "/hr/attendance/schedule/batch", {
        "deptId": DEPT_TECH, "month": MONTH, "shiftId": shift_id, "overwrite": 1,
    }, tok=admin)
    generated = body.get("data") or 0
    ck("批量排班生成记录", body.get("code") == 200 and generated > 0, f"count={generated} msg={body.get('message')}")

    code, body = req("GET", f"/hr/attendance/schedule?deptId={DEPT_TECH}&month={MONTH}", tok=admin)
    schedules = body.get("data") or []
    ck("排班列表可查（含休息日与工作日）",
       len(schedules) > 0 and any(s.get("shiftName") for s in schedules) and any(not s.get("shiftName") for s in schedules),
       f"rows={len(schedules)}")

    # ============ 打卡 ============
    # 前置：把「今天」钉成工作日。
    #
    # 批量排班按自然周把周末标成休息日（shift_id 为空），于是本脚本在周六/周日跑时，
    # 「今日卡片已排班」及其后 8 条打卡断言会全红——而代码一行没变。
    # 这类「日历决定成败」的假红灯最容易误导人，前置状态由用例自己建立。
    mysql_exec(f"DELETE FROM {DB}.oa_attendance WHERE attendance_date='{TODAY}' "
               f"AND user_id IN ({TECH_STAFF1},{TECH_STAFF2})")
    mysql_exec(f"DELETE FROM {DB}.oa_shift_schedule WHERE schedule_date='{TODAY}' "
               f"AND user_id IN ({TECH_STAFF1},{TECH_STAFF2})")
    mysql_exec(f"INSERT INTO {DB}.oa_shift_schedule (user_id, schedule_date, shift_id, day_type, settled) "
               f"VALUES ({TECH_STAFF1}, '{TODAY}', {shift_id}, 1, 0), "
               f"({TECH_STAFF2}, '{TODAY}', {shift_id}, 1, 0)")

    code, body = req("GET", "/hr/attendance/today", tok=tech)
    today_info = body.get("data") or {}
    ck("今日卡片已排班", today_info.get("scheduled") is True, json.dumps(today_info, ensure_ascii=False)[:120])
    ck("可打上班卡", today_info.get("canClockIn") is True, "")

    code, body = req("POST", "/hr/attendance/clock", {"direction": 1}, tok=tech)
    ck("上班打卡成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("POST", "/hr/attendance/clock", {"direction": 1}, tok=tech)
    ck("重复上班打卡被拒（6206）", body.get("code") == 6206, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("POST", "/hr/attendance/clock", {"direction": 2}, tok=tech)
    ck("下班打卡成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("GET", "/hr/attendance/today", tok=tech)
    info = body.get("data") or {}
    ck("今日卡片回填打卡时间与工时", info.get("checkInTime") and info.get("checkOutTime")
       and info.get("workedMinutes") is not None, json.dumps(info, ensure_ascii=False)[:160])

    # admin 属于部门 1，未排班
    code, body = req("POST", "/hr/attendance/clock", {"direction": 1}, tok=admin)
    ck("未排班打卡被拒（6201）", body.get("code") == 6201, f"code={body.get('code')} msg={body.get('message')}")

    # ============ 日结 / 月结 ============
    code, body = req("POST", f"/hr/attendance/settle?date={TODAY}&force=true", tok=admin)
    ck("日结执行成功", body.get("code") == 200 and (body.get("data") or 0) > 0, f"data={body.get('data')}")

    code, body = req("GET", f"/hr/attendance/page?month={MONTH}&pageNo=1&pageSize=50", tok=admin)
    page_rows = ((body.get("data") or {}).get("records")) or []
    mine = [r for r in page_rows if r.get("userId") == TECH_STAFF1 and r.get("attendanceDate") == TODAY]
    ck("考勤明细生成当日记录", len(mine) == 1, f"rows={len(page_rows)}")
    ck("明细带工时与状态", mine and mine[0].get("workDuration") is not None
       and mine[0].get("attendanceStatusLabel"), json.dumps(mine[0], ensure_ascii=False)[:160] if mine else "")

    before = len(page_rows)
    req("POST", f"/hr/attendance/settle?date={TODAY}&force=true", tok=admin)
    code, body = req("GET", f"/hr/attendance/page?month={MONTH}&pageNo=1&pageSize=50", tok=admin)
    after = len(((body.get("data") or {}).get("records")) or [])
    ck("日结幂等（重复执行不产生重复行）", before == after, f"{before} -> {after}")

    code, body = req("POST", f"/hr/attendance/rebuild?month={MONTH}", tok=admin)
    ck("月度重算成功", body.get("code") == 200 and (body.get("data") or 0) > 0, f"data={body.get('data')}")

    code, body = req("GET", f"/hr/attendance/month?month={MONTH}&pageNo=1&pageSize=50", tok=admin)
    month_rows = ((body.get("data") or {}).get("records")) or []
    mine_month = [r for r in month_rows if r.get("userId") == TECH_STAFF1]
    ck("月度汇总生成记录", len(mine_month) == 1, f"rows={len(month_rows)}")
    ck("应出勤天数大于 0", mine_month and float(mine_month[0].get("shouldDays") or 0) > 0,
       str(mine_month[0].get("shouldDays") if mine_month else None))

    # 封账后重算不覆盖
    code, body = req("POST", f"/hr/attendance/lock?month={MONTH}", tok=admin)
    ck("考勤封账成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")

    locked_actual = mine_month[0].get("actualDays") if mine_month else None
    req("POST", f"/hr/attendance/rebuild?month={MONTH}", tok=admin)
    code, body = req("GET", f"/hr/attendance/month?month={MONTH}&pageNo=1&pageSize=50", tok=admin)
    after_rows = [r for r in (((body.get("data") or {}).get("records")) or []) if r.get("userId") == TECH_STAFF1]
    ck("封账后重算不覆盖结果",
       after_rows and after_rows[0].get("status") == 2 and after_rows[0].get("actualDays") == locked_actual,
       f"status={after_rows[0].get('status') if after_rows else None}")

    code, body = req("POST", f"/hr/attendance/unlock?month={MONTH}&reason=", tok=admin)
    ck("解封未填原因被拒", body.get("code") == 400, f"code={body.get('code')}")

    code, body = req("POST", f"/hr/attendance/unlock?month={MONTH}&reason={quote('自动化测试收尾')}", tok=admin)
    ck("超管解封成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")

    cleanup(admin)
    return summary()


def cleanup(admin):
    """还原测试改动：先清考勤/排班（否则班次删不掉），再清档案侧表与岗位。"""
    # 重算会把整月都日结，因此按月整体清理，而不是只删今天
    mysql(f"DELETE FROM oa_system.oa_attendance_month WHERE month='{MONTH}'")
    mysql(f"DELETE FROM oa_system.oa_attendance WHERE attendance_date LIKE '{MONTH}%'")
    mysql(f"DELETE FROM oa_system.oa_shift_schedule WHERE schedule_date LIKE '{MONTH}%'")
    if STATE.get("shiftId"):
        req("DELETE", f"/hr/attendance/shift/{STATE['shiftId']}", tok=admin)
    if STATE.get("certId"):
        req("DELETE", f"/hr/employee/{TECH_STAFF1}/cert/{STATE['certId']}", tok=admin)
    if STATE.get("contractId"):
        req("DELETE", f"/hr/employee/{TECH_STAFF1}/contract/{STATE['contractId']}", tok=admin)

    # 还原档案改动与离职联动
    mysql(f"UPDATE oa_system.oa_user SET position_id=NULL, position=NULL, direct_leader_id=NULL, "
          f"employment_status=3, status=1 WHERE id={TECH_STAFF1}")
    mysql(f"UPDATE oa_system.oa_user SET employment_status=3, status=1 WHERE id={TECH_STAFF2}")
    mysql("DELETE FROM oa_system.oa_employee_change WHERE user_id IN (101809,101810) "
          "AND remark IN ('已提交离职申请','撤回离职','验证离职联动禁用账号')")

    if STATE.get("positionId"):
        code, body = req("DELETE", f"/hr/position/{STATE['positionId']}", tok=admin)
        print(f"cleanup position -> code={body.get('code')}")
    print("cleanup done:", mysql(
        "SELECT CONCAT('position=', (SELECT COUNT(*) FROM oa_system.oa_position WHERE is_deleted=0), "
        "' shift=', (SELECT COUNT(*) FROM oa_system.oa_shift WHERE is_deleted=0), "
        "' att=', (SELECT COUNT(*) FROM oa_system.oa_attendance), "
        "' month=', (SELECT COUNT(*) FROM oa_system.oa_attendance_month))"))


if __name__ == "__main__":
    sys.exit(main())
