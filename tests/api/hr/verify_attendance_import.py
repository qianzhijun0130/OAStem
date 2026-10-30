#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BIZ-OPT-08 考勤机数据导入 + 漏卡自助处理。

为什么要单独写一条：既有 verify_p0 只覆盖「网页打卡 → 日结 → 月结」，
考勤机导入是新增的数据源，它和日结共用同一条考勤行，出错方式完全不同：

- 重复导入不幂等 → 月结工时翻倍（最危险，且当月看不出来）；
- 判定另写一套 → 导入说迟到 30 分、日结说 25 分，同一行两个数；
- 未排班也写记录 → 月结分母失准；
- 漏卡补卡占每月 3 次额度 → 真实漏卡多的人反而补不了。

用固定月份 2026-11（下个月，无历史数据干扰），跑完按用户 + 月份清场。
班次自建：本机 oa_shift 里 17 条全被历史测试逻辑删了，若复用既有班次，
「未排班」与「班次已删除」两种失败会混在一起，看不出真正的回归。
"""
import io
import sys
import time
from datetime import date, timedelta
from pathlib import Path

from openpyxl import Workbook

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, ck, mysql, mysql_exec, req, summary, token, upload  # noqa: E402

MONTH = "2026-11"
# 张明 / 李伟 / 王芳，同属部门 2
USERS = [(101808, "JSB022"), (101809, "JSB023"), (101810, "JSB024")]

HEADER_FULL = ("工号", "日期", "打卡时间", "方向")
HEADER_TIME_ONLY = ("工号", "打卡时间")
SHIFT_CODE = "SHIFT-MACHINE-T"


def xlsx(rows, header=HEADER_FULL):
    wb = Workbook()
    ws = wb.active
    ws.append(list(header))
    for row in rows:
        ws.append(list(row))
    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()


def cleanup():
    """按用户 + 月份清场（考勤、排班、封账记录、补卡单及其审批实例）"""
    ids = ",".join(str(u) for u, _ in USERS)
    # 写操作用 mysql_exec：mysql() 带 --force 会把失败吞掉，清场失败会让后面全测旧数据
    mysql_exec(f"DELETE FROM {DB}.oa_attendance WHERE user_id IN ({ids}) "
               f"AND attendance_date >= '{MONTH}-01' AND attendance_date < '2026-12-01'")
    mysql_exec(f"DELETE FROM {DB}.oa_shift_schedule WHERE user_id IN ({ids}) "
               f"AND schedule_date >= '{MONTH}-01' AND schedule_date < '2026-12-01'")
    mysql_exec(f"DELETE FROM {DB}.oa_attendance_month WHERE user_id IN ({ids}) AND month = '{MONTH}'")
    mysql_exec(f"DELETE FROM {DB}.oa_card_supplement WHERE user_id IN ({ids}) "
               f"AND card_date >= '{MONTH}-01' AND card_date < '2026-12-01'")
    # 补卡单对应的审批实例（按标题定位，避免误删他人数据）
    mysql_exec(f"DELETE FROM {DB}.oa_approval_instance WHERE applicant_id IN ({ids}) "
               f"AND title LIKE '补卡申请 - {MONTH}%'")
    # 补卡段用的是过去的 10-08~10-11（补卡只允许回溯 30 天），一并清掉
    mysql_exec(f"DELETE FROM {DB}.oa_attendance WHERE user_id=101810 "
               f"AND attendance_date BETWEEN '2026-10-08' AND '2026-10-11'")
    mysql_exec(f"DELETE FROM {DB}.oa_shift_schedule WHERE user_id=101810 "
               f"AND schedule_date BETWEEN '2026-10-08' AND '2026-10-11'")
    mysql_exec(f"DELETE FROM {DB}.oa_card_supplement WHERE user_id=101810 "
               f"AND card_date BETWEEN '2026-10-08' AND '2026-10-11'")
    mysql_exec(f"DELETE FROM {DB}.oa_approval_instance WHERE applicant_id=101810 "
               f"AND title LIKE '补卡申请 - 2026-10%'")


def ensure_shift(admin):
    """幂等取/建测试班次（09:00-18:00，休息 60 分钟，迟到宽限 5 分钟）"""
    found = mysql(f"SELECT id FROM {DB}.oa_shift WHERE shift_code='{SHIFT_CODE}' AND is_deleted=0 LIMIT 1")
    if found:
        return int(found)
    _, body = req("POST", "/hr/attendance/shift", {
        "shiftCode": SHIFT_CODE, "shiftName": "考勤机导入测试班",
        "workStart": "09:00:00", "workEnd": "18:00:00",
        "restMinutes": 60, "lateGrace": 5, "earlyGrace": 0,
        "flexible": 0, "freeClock": 0, "status": 1,
    }, tok=admin)
    return int(body.get("data") or 0)


def seed_schedules(days, shift_id, users=None):
    """造排班：指定用户 × 指定日期"""
    users = users or USERS
    values = [f"({uid}, '{d}', {shift_id}, 1, 0)" for uid, _ in users for d in days]
    mysql_exec(f"INSERT INTO {DB}.oa_shift_schedule (user_id, schedule_date, shift_id, day_type, settled) "
               f"VALUES {','.join(values)}")


def attend(uid, d):
    rows = mysql(f"SELECT check_in_time, check_out_time, check_in_source, check_out_source, "
                 f"work_duration, late_minutes, attendance_status, card_missing "
                 f"FROM {DB}.oa_attendance WHERE user_id={uid} AND attendance_date='{d}' AND is_deleted=0")
    return rows.split("\t") if rows else []


def main():
    admin = token(1, "admin")
    emp_staff = token(101809, "tech_staff1")

    print("== 0. 准备：清场 + 班次 + 排班 ==")
    cleanup()
    shift_id = ensure_shift(admin)
    ck("测试班次就绪", shift_id > 0, f"shiftId={shift_id}")
    days = ["2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05"]
    seed_schedules(days, shift_id)
    ck("排班就绪且班次正确",
       mysql(f"SELECT COUNT(*) FROM {DB}.oa_shift_schedule "
             f"WHERE schedule_date='2026-11-02' AND shift_id={shift_id}") == "3",
       f"库中班次={mysql(f'SELECT GROUP_CONCAT(DISTINCT shift_id) FROM {DB}.oa_shift_schedule WHERE schedule_date=2026-11-02')}")

    print("== 1. 首次导入：3 人 × 2 天 × 2 次 ==")
    rows = []
    for uid, no in USERS:
        for d in ("2026-11-02", "2026-11-03"):
            rows.append((no, d, f"{d} 09:00:00", "上班"))
            rows.append((no, d, f"{d} 18:00:00", "下班"))
    _, body = upload("/hr/attendance/import/machine", "machine1.xlsx", xlsx(rows), tok=admin)
    data = body.get("data") or {}
    ck("导入成功：12 行 → 6 人天",
       data.get("total") == 12 and data.get("imported") == 6 and data.get("failed") == 0,
       f"total={data.get('total')} imported={data.get('imported')} failed={data.get('failed')} "
       f"{data.get('errors')}")

    info = attend(101808, "2026-11-02")
    ck("写入考勤行且来源=考勤机", len(info) >= 5 and info[2] == "2" and info[3] == "2",
       f"inSource={info[2] if len(info) > 2 else '-'} outSource={info[3] if len(info) > 3 else '-'}")
    ck("工时 8.0 小时（9:00-18:00 扣 60 分钟休息）", len(info) > 4 and info[4].startswith("8.0"),
       info[4] if len(info) > 4 else "-")
    ck("准时上班不判迟到", len(info) > 5 and info[5] == "0", f"late={info[5] if len(info) > 5 else '-'}")

    print("== 2. 幂等：同一文件重传 ==")
    _, body = upload("/hr/attendance/import/machine", "machine1.xlsx", xlsx(rows), tok=admin)
    data = body.get("data") or {}
    ck("重传不新增记录", data.get("imported") == 0, str(data.get("imported")))
    ck("重传全部计为重复（6 人天）", data.get("duplicate") == 6, str(data.get("duplicate")))
    ck("重传后库里仍只有一条",
       mysql(f"SELECT COUNT(*) FROM {DB}.oa_attendance WHERE user_id=101808 "
             f"AND attendance_date='2026-11-02' AND is_deleted=0") == "1", "")

    print("== 3. 判定走 AttendanceRules：迟到分钟 ==")
    # 09:30 上班、宽限 5 分钟 → 判迟到；迟到分钟记全部差值 30（宽限只决定是否判，不减免分钟）
    _, body = upload("/hr/attendance/import/machine", "machine2.xlsx",
                     xlsx([("JSB022", "2026-11-04", "2026-11-04 09:30:00", "上班"),
                           ("JSB022", "2026-11-04", "2026-11-04 18:05:00", "下班")]), tok=admin)
    ck("首次写入该日考勤成功", (body.get("data") or {}).get("imported") == 1,
       str((body.get("data") or {}).get("imported")))
    info = attend(101808, "2026-11-04")
    ck("判迟到且迟到 30 分钟", len(info) > 6 and info[6] == "2" and info[5] == "30",
       f"status={info[6] if len(info) > 6 else '-'} late={info[5] if len(info) > 5 else '-'}")

    print("== 4. 无方向列：单次刷卡推断为上班卡 ==")
    _, body = upload("/hr/attendance/import/machine", "machine3.xlsx",
                     xlsx([("JSB023", "2026-11-05 09:05:00")], header=HEADER_TIME_ONLY), tok=admin)
    ck("两列格式（工号+时间）可导入", (body.get("data") or {}).get("imported") == 1,
       str((body.get("data") or {}).get("failed")))
    info = attend(101809, "2026-11-05")
    ck("单次刷卡只写入上班侧", len(info) > 2 and info[1] in ("", "NULL") and info[2] == "2",
       f"out={info[1] if len(info) > 1 else '-'} inSource={info[2] if len(info) > 2 else '-'}")
    ck("card_missing=2（缺下班）", len(info) > 7 and info[7] == "2",
       f"missing={info[7] if len(info) > 7 else '-'}")

    print("== 5. 漏卡清单 ==")
    _, body = req("GET", f"/hr/attendance/missing?month={MONTH}", tok=admin)
    page = body.get("data") or {}
    listed = [r for r in (page.get("records") or []) if r["userId"] == 101809]
    ck("漏卡清单含缺下班卡的记录", any(r["cardMissing"] == 2 for r in listed),
       f"n={len(page.get('records') or [])}")
    ck("漏卡项带方向明细", all(r["missingDirections"] for r in listed), "")
    _, body = req("GET", f"/hr/attendance/missing/my?month={MONTH}", tok=emp_staff)
    mine = body.get("data") or []
    ck("我的漏卡只含本人", all(r["userId"] == 101809 for r in mine) and len(mine) >= 1, f"n={len(mine)}")

    print("== 6. 漏卡补卡不占每月 3 次额度 ==")
    # 补卡限制「只能回溯 30 天」，所以这一段必须用过去的日期，不能用前面那个未来月份。
    # 用王芳 10-08~10-11 这个独立区间，进来先清干净，避免与既有考勤串味。
    card_days = ["2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11"]
    wang = [(101810, "JSB024")]
    mysql_exec(f"DELETE FROM {DB}.oa_attendance WHERE user_id=101810 "
               f"AND attendance_date BETWEEN '{card_days[0]}' AND '{card_days[-1]}'")
    mysql_exec(f"DELETE FROM {DB}.oa_shift_schedule WHERE user_id=101810 "
               f"AND schedule_date BETWEEN '{card_days[0]}' AND '{card_days[-1]}'")
    seed_schedules(card_days, shift_id, users=wang)
    # 每天只打上班卡 → 4 天都成为「缺下班卡」的漏卡事实
    _, body = upload("/hr/attendance/import/machine", "machine4.xlsx",
                     xlsx([("JSB024", d, f"{d} 09:00:00", "上班") for d in card_days]), tok=admin)
    ck("漏卡事实造好（4 天缺下班卡）", (body.get("data") or {}).get("imported") == 4,
       str((body.get("data") or {}).get("imported")))

    wang_token = token(101810, "tech_staff2")
    ok_cnt, limited, others = 0, 0, []
    for d in card_days:
        _, body = req("POST", "/hr/request", {
            "bizType": 3, "cardDate": d, "direction": 2,
            "cardTime": f"{d}T18:00:00", "reason": "下班忘打卡，门禁无记录",
        }, tok=wang_token)
        if body.get("code") == 200:
            ok_cnt += 1
        elif body.get("code") == 6203:
            limited += 1
        else:
            others.append(f"{d}:{body.get('code')}")
    ck("4 次漏卡补卡全部成功（不占额度）", ok_cnt == 4 and limited == 0,
       f"ok={ok_cnt} limited={limited} others={others}")
    types = mysql(f"SELECT GROUP_CONCAT(DISTINCT card_type) FROM {DB}.oa_card_supplement "
                  f"WHERE user_id=101810 AND card_date >= '{card_days[0]}' "
                  f"AND card_date <= '{card_days[-1]}'")
    ck("补卡类型记为漏卡（card_type=2）", types == "2", f"types={types}")

    print("== 7. 封账月份拒绝导入 ==")
    mysql_exec(f"INSERT INTO {DB}.oa_attendance_month (user_id, month, should_days, actual_days, status) "
               f"VALUES (101810, '{MONTH}', 21, 21, 2)")
    _, body = upload("/hr/attendance/import/machine", "machine5.xlsx",
                     xlsx([("JSB024", "2026-11-02", "2026-11-02 09:00:00", "上班")]), tok=admin)
    ck("已封账月份整批拒绝（6208）", body.get("code") == 6208,
       f"code={body.get('code')} msg={body.get('message')}")
    mysql_exec(f"DELETE FROM {DB}.oa_attendance_month WHERE user_id=101810 AND month='{MONTH}'")

    print("== 8. 逐行校验：未排班 / 工号不存在 / 表头缺失 ==")
    _, body = upload("/hr/attendance/import/machine", "machine6.xlsx",
                     xlsx([("JSB022", "2026-11-30", "2026-11-30 09:00:00", "上班"),
                           ("NO-SUCH-001", "2026-11-02", "2026-11-02 09:00:00", "上班")]), tok=admin)
    data = body.get("data") or {}
    msgs = " ".join(e["message"] for e in data.get("errors") or [])
    ck("未排班与不存在的工号都被拦下", data.get("failed") == 2, str(data.get("failed")))
    ck("错误信息可定位原因", "未排班" in msgs and "不存在" in msgs, msgs[:90])
    _, body = upload("/hr/attendance/import/machine", "bad.xlsx",
                     xlsx([("JSB022", "2026-11-02")], header=("备注", "说明")), tok=admin)
    ck("表头缺关键列被拒（6209）", body.get("code") == 6209, f"code={body.get('code')}")

    print("== 9. 打卡来源在明细接口可见 ==")
    _, body = req("GET", f"/hr/attendance/page?month={MONTH}&pageSize=50", tok=admin)
    recs = (body.get("data") or {}).get("records") or []
    machine = [r for r in recs if r.get("checkInSource") == 2]
    ck("明细接口返回打卡来源", len(machine) > 0, f"考勤机来源行数={len(machine)}")

    print("== 10. 互不覆盖：网页打卡存在时不改该侧 ==")
    # 张明 11-03 先有网页打卡，再导考勤机数据 → 保留网页打卡原值并给提示
    mysql_exec(f"UPDATE {DB}.oa_attendance SET check_in_time='{MONTH}-03 08:55:00', check_in_source=1, "
               f"check_out_time=NULL, check_out_source=NULL "
               f"WHERE user_id=101808 AND attendance_date='{MONTH}-03'")
    _, body = upload("/hr/attendance/import/machine", "machine7.xlsx",
                     xlsx([("JSB022", "2026-11-03", "2026-11-03 09:10:00", "上班")]), tok=admin)
    data = body.get("data") or {}
    kept = mysql(f"SELECT check_in_time FROM {DB}.oa_attendance WHERE user_id=101808 "
                 f"AND attendance_date='{MONTH}-03'")
    ck("网页打卡不被 Excel 覆盖", kept.startswith(f"{MONTH}-03 08:55"), f"checkIn={kept}")
    ck("跳过原因以 warning 形式返回", len(data.get("warnings") or []) >= 1, str(data.get("warnings")))

    print("== 11. 性能：1 万行 ==")
    cleanup()
    perf_days = [(date(2026, 11, 1) + timedelta(days=i)).isoformat() for i in range(30)]
    seed_schedules(perf_days, shift_id)
    big = []
    for uid, no in USERS:
        for d in perf_days:
            big.append((no, d, f"{d} 09:00:00", "上班"))
            big.append((no, d, f"{d} 18:00:00", "下班"))
    # 补到 1 万行：同人同天多个时刻（真实导出的原始刷卡就是这种形态，中间记录应被忽略）
    filler = 0
    while len(big) < 10000:
        d = perf_days[filler % 30]
        big.append((USERS[filler % 3][1], d, f"{d} 12:{filler % 60:02d}:00", "上班"))
        filler += 1
    started = time.time()
    _, body = upload("/hr/attendance/import/machine", "big.xlsx", xlsx(big[:10000]), tok=admin,
                     timeout=180)
    elapsed = time.time() - started
    data = body.get("data") or {}
    ck(f"1 万行导入 ≤ 10s（实测 {elapsed:.2f}s）", data.get("total") == 10000 and elapsed < 10,
       f"total={data.get('total')} imported={data.get('imported')} {elapsed:.2f}s")

    print("== 12. 清理 ==")
    cleanup()
    left = mysql(f"SELECT COUNT(*) FROM {DB}.oa_attendance WHERE user_id IN (101808,101809,101810) "
                 f"AND attendance_date >= '{MONTH}-01' AND attendance_date < '2026-12-01'")
    ck("测试数据清理干净", left == "0", f"left={left}")

    return summary()


if __name__ == "__main__":
    sys.exit(main())
