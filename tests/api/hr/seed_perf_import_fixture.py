#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UI 回归用的绩效导入数据夹具（不是用例，没有断言）。

自建全套（指标 + 方案 + 等级 + 已归档周期），一个都不依赖库里既有数据：
`verify_perf.py` 会测「删除周期/方案/指标」，把列表最新那几条逻辑删掉，
依赖它们的夹具会在别的用例跑完后突然失效（本轮实测踩过）。

编码统一带 `V-UI-PERF` 前缀，与接口用例（`V-IMP-*` / `V-PLAN-IMPORT-T`）互不干扰。
用法：python seed_perf_import_fixture.py [--clean]
"""
import io
import sys
from pathlib import Path

from openpyxl import Workbook

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, mysql, mysql_exec, token, upload  # noqa: E402

PLAN_CODE = "V-UI-PERF-PLAN"
CYCLE_CODE = "V-UI-PERF-CYCLE"
UID = 101808
WORK_NO = "JSB022"
INDICATORS = [
    ("V-UI-PERF-WORK", "UI校验-业绩", 1, 100),
    ("V-UI-PERF-BONUS", "UI校验-加分", 3, 0),
    ("V-UI-PERF-PEN", "UI校验-扣分", 4, 0),
]
GRADES = [("A", "优秀", 90, 120, 1.20, 1), ("B", "良好", 80, 89.9, 1.00, 2),
          ("C", "合格", 70, 79.9, 0.80, 3), ("D", "待改进", 0, 69.9, 0.60, 4)]
HEADER = ["工号", "姓名", "UI校验-业绩(100%)", "UI校验-加分(0%)", "UI校验-扣分(0%)", "总分", "评语"]


def clean():
    cid = mysql(f"SELECT id FROM {DB}.oa_perf_cycle WHERE cycle_code='{CYCLE_CODE}' LIMIT 1")
    if cid:
        mysql_exec(f"DELETE i FROM {DB}.oa_perf_sheet_item i WHERE i.sheet_id IN "
                   f"(SELECT id FROM {DB}.oa_perf_sheet WHERE cycle_id={cid})")
        mysql_exec(f"DELETE FROM {DB}.oa_perf_sheet WHERE cycle_id={cid}")
        mysql_exec(f"DELETE FROM {DB}.oa_perf_cycle WHERE id={cid}")
    pid = mysql(f"SELECT id FROM {DB}.oa_perf_plan WHERE plan_code='{PLAN_CODE}' LIMIT 1")
    if pid:
        mysql_exec(f"DELETE FROM {DB}.oa_perf_plan_grade WHERE plan_id={pid}")
        mysql_exec(f"DELETE FROM {DB}.oa_perf_plan_indicator WHERE plan_id={pid}")
        mysql_exec(f"DELETE FROM {DB}.oa_perf_plan WHERE id={pid}")
    for code, _, _, _ in INDICATORS:
        mysql_exec(f"DELETE FROM {DB}.oa_perf_indicator WHERE indicator_code='{code}'")


def seed():
    admin = token(1, "admin")
    clean()
    ids = {}
    for code, name, typ, _ in INDICATORS:
        mysql_exec(f"INSERT INTO {DB}.oa_perf_indicator "
                   f"(indicator_code, indicator_name, indicator_type, status) "
                   f"VALUES ('{code}', '{name}', {typ}, 1)")
        ids[code] = mysql(f"SELECT id FROM {DB}.oa_perf_indicator WHERE indicator_code='{code}'")
    mysql_exec(f"INSERT INTO {DB}.oa_perf_plan (plan_code, plan_name, cycle_type, score_mode, status) "
               f"VALUES ('{PLAN_CODE}', 'UI校验用方案', 2, 1, 1)")
    pid = mysql(f"SELECT id FROM {DB}.oa_perf_plan WHERE plan_code='{PLAN_CODE}'")
    for sort, (code, _, _, weight) in enumerate(INDICATORS, start=1):
        mysql_exec(f"INSERT INTO {DB}.oa_perf_plan_indicator (plan_id, indicator_id, weight, sort) "
                   f"VALUES ({pid}, {ids[code]}, {weight}, {sort})")
    for code, name, lo, hi, coef, sort in GRADES:
        mysql_exec(f"INSERT INTO {DB}.oa_perf_plan_grade "
                   f"(plan_id, grade_code, grade_name, min_score, max_score, coefficient, sort) "
                   f"VALUES ({pid}, '{code}', '{name}', {lo}, {hi}, {coef}, {sort})")
    # start_date 取 2026-09-01（比库里现有周期新），页面上会排在第一位并默认选中
    mysql_exec(f"INSERT INTO {DB}.oa_perf_cycle (cycle_code, cycle_name, plan_id, stage, status, "
               f"start_date, end_date) VALUES ('{CYCLE_CODE}', 'UI校验周期', {pid}, 6, 2, "
               f"'2026-09-01', '2026-09-30')")
    cid = mysql(f"SELECT id FROM {DB}.oa_perf_cycle WHERE cycle_code='{CYCLE_CODE}'")

    wb = Workbook()
    ws = wb.active
    ws.append(HEADER)
    ws.append([WORK_NO, "张明", 90, 5, None, 95, "历史台账迁移"])
    buf = io.BytesIO()
    wb.save(buf)
    _, body = upload(f"/hr/perf/import?cycleId={cid}", "ui_perf_fixture.xlsx",
                     buf.getvalue(), tok=admin)
    data = body.get("data") or {}
    print(f"seed: cycleId={cid} imported={data.get('imported')} failed={data.get('failed')} "
          f"errors={data.get('errors')}")


if __name__ == "__main__":
    if "--clean" in sys.argv:
        clean()
        print("cleaned")
    else:
        seed()
