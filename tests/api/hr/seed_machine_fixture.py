#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UI 回归用的考勤机数据夹具（不是用例，没有断言）。

造两天的数据，覆盖页面上要看的两种形态：

- 10-28：上、下班都有考勤机刷卡 → 明细里应显示「考勤机」来源标签；
- 10-29：只有上班卡 → 应出现在「漏卡待处理」。

日期取当月（2026-10）而不是未来月份，因为页面默认就看当月，省得脚本再切月份。
用法：python seed_machine_fixture.py [--clean]
"""
import io
import sys
from pathlib import Path

from openpyxl import Workbook

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, mysql, mysql_exec, req, token, upload  # noqa: E402

UID = 101808
WORK_NO = "JSB022"
SHIFT_CODE = "SHIFT-MACHINE-T"
DAYS = ["2026-10-28", "2026-10-29"]


def ensure_shift(admin):
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


def clean():
    mysql_exec(f"DELETE FROM {DB}.oa_attendance WHERE user_id={UID} "
               f"AND attendance_date BETWEEN '{DAYS[0]}' AND '{DAYS[-1]}'")
    mysql_exec(f"DELETE FROM {DB}.oa_shift_schedule WHERE user_id={UID} "
               f"AND schedule_date BETWEEN '{DAYS[0]}' AND '{DAYS[-1]}'")


def seed():
    admin = token(1, "admin")
    shift_id = ensure_shift(admin)
    if not shift_id:
        raise SystemExit("没有可用班次")
    clean()
    values = ",".join(f"({UID}, '{d}', {shift_id}, 1, 0)" for d in DAYS)
    mysql_exec(f"INSERT INTO {DB}.oa_shift_schedule (user_id, schedule_date, shift_id, day_type, settled) "
               f"VALUES {values}")
    rows = [
        (WORK_NO, DAYS[0], f"{DAYS[0]} 09:00:00", "上班"),
        (WORK_NO, DAYS[0], f"{DAYS[0]} 18:00:00", "下班"),
        (WORK_NO, DAYS[1], f"{DAYS[1]} 09:00:00", "上班"),
    ]
    wb = Workbook()
    ws = wb.active
    ws.append(["工号", "日期", "打卡时间", "方向"])
    for r in rows:
        ws.append(list(r))
    buf = io.BytesIO()
    wb.save(buf)
    _, body = upload("/hr/attendance/import/machine", "ui_fixture.xlsx", buf.getvalue(), tok=admin)
    data = body.get("data") or {}
    print(f"seed: imported={data.get('imported')} failed={data.get('failed')} "
          f"errors={data.get('errors')}")


if __name__ == "__main__":
    if "--clean" in sys.argv:
        clean()
        print("cleaned")
    else:
        seed()
