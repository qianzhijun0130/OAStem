#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""薪酬 UI 回归的「考勤封账」夹具（不是用例，没有断言）。

背景：核薪的前置条件是「该月考勤已封账」（6402），否则考勤一变工资就对不上。
而这个封账记录会被 verify_salary.py 等接口用例清理掉，于是 hr_salary.js
只要排在任何一条薪资用例之后，列表就是空的，点「明细」直接 30 秒超时——
表现为「看起来是页面崩了」，实际是夹具缺失。

把造数放在 python 侧（node 连不了库），由 UI 脚本开跑前调一次。
用法：python seed_salary_month_fixture.py [YYYY-MM]
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, mysql_exec  # noqa: E402

DEPT_ID = 2


def default_month():
    """与页面默认值一致：上个月"""
    from datetime import date
    today = date.today()
    year, month = (today.year, today.month - 1) if today.month > 1 else (today.year - 1, 12)
    return f"{year}-{month:02d}"


def main(month):
    # 只给「在职且在部门 2」的人造，与 hr_salary.js 建的档案范围保持一致（核薪按部门走）
    mysql_exec(f"DELETE FROM {DB}.oa_attendance_month WHERE month='{month}' "
               f"AND user_id IN (SELECT id FROM {DB}.oa_user WHERE dept_id={DEPT_ID} "
               f"AND is_deleted=0 AND employment_status IN (2,3,4))")
    mysql_exec(
        f"INSERT INTO {DB}.oa_attendance_month (user_id, month, should_days, actual_days, status) "
        f"SELECT id, '{month}', 21, 21, 2 FROM {DB}.oa_user "
        f"WHERE dept_id={DEPT_ID} AND is_deleted=0 AND employment_status IN (2,3,4)")
    n = mysql_exec(f"SELECT COUNT(*) FROM {DB}.oa_attendance_month WHERE month='{month}'")
    print(f"salary-month fixture: month={month} locked_rows={n}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else default_month())
