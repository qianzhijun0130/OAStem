#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BIZ-OPT-10（结果导入）验证：历史绩效迁移。

为什么单独写一条：既有 verify_perf.py 覆盖的是「走完四道门槛」的流程，
结果导入是另一条写入路径——它直接写终态，出错方式完全不同：

- 绕过流程：进行中的周期若允许导入，等于把自评/上级评/校准/确认全部架空，且事后看不出；
- 分数算错：不复用 PerfScoreCalculator 就会出现「导入 95 分、系统算出 90 分」；
- 来源不可辨：不标 source，员工看到分数无法判断是不是自己上司打的；
- 漏列指标：模板少一列不报错，但所有人分数系统性偏低，要等发工资才发现。

用已归档的周期 17（plan 17：业绩 100% + 加分项 0 + 扣分项 0，等级 A/B/C/D），
跑完按周期 + 用户清场。
"""
import io
import sys
from pathlib import Path

from openpyxl import Workbook, load_workbook

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, ck, mysql, mysql_exec, req, summary, token, upload  # noqa: E402

# 周期、方案、指标、等级全部自建，一个都不依赖库里既有数据。
# 原因是本轮实测：verify_perf.py 会测「删除周期/方案/指标」，把列表最新那几条逻辑删掉，
# 于是写死 cycleId=17、planId=18 的用例在它跑完之后全部失效（导入直接报「周期不存在」，
# 指标也变成「未知指标」）。用例的前置状态必须自己建立。
PLAN_CODE = "V-PLAN-IMPORT-T"
CYCLE_CODE = "V-CYCLE-IMPORT-OK"
ACTIVE_CODE = "V-CYCLE-IMPORT-T"

# 编码/名称带独立前缀，避免与库里残留数据撞唯一索引
INDICATORS = [
    ("V-IMP-PERF", "导入校验-业绩", 1, 100),
    ("V-IMP-BONUS", "导入校验-加分", 3, 0),
    ("V-IMP-PEN", "导入校验-扣分", 4, 0),
]
# grade_name 是 NOT NULL 且无默认值，必须显式给
GRADES = [("A", "优秀", 90, 120, 1.20, 1), ("B", "良好", 80, 89.9, 1.00, 2),
          ("C", "合格", 70, 79.9, 0.80, 3), ("D", "待改进", 0, 69.9, 0.60, 4)]

COL_PERF = "导入校验-业绩(100%)"
COL_BONUS = "导入校验-加分(0%)"
COL_PENALTY = "导入校验-扣分(0%)"
HEADER = ["工号", "姓名", COL_PERF, COL_BONUS, COL_PENALTY, "总分", "评语"]


def xlsx(rows, header=None):
    wb = Workbook()
    ws = wb.active
    ws.append(list(header or HEADER))
    for r in rows:
        ws.append(list(r))
    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()


def drop_plan():
    """物理删掉自建方案及其等级/指标关联/指标本体（逻辑删会留在库里干扰下次重建）"""
    pid = mysql(f"SELECT id FROM {DB}.oa_perf_plan WHERE plan_code='{PLAN_CODE}' LIMIT 1")
    if pid:
        mysql_exec(f"DELETE FROM {DB}.oa_perf_plan_grade WHERE plan_id={pid}")
        mysql_exec(f"DELETE FROM {DB}.oa_perf_plan_indicator WHERE plan_id={pid}")
        mysql_exec(f"DELETE FROM {DB}.oa_perf_plan WHERE id={pid}")
    for code, _, _, _ in INDICATORS:
        mysql_exec(f"DELETE FROM {DB}.oa_perf_indicator WHERE indicator_code='{code}'")


def ensure_plan():
    """自建「业绩 100% + 加分项 0 + 扣分项 0」的方案与 A/B/C/D 等级，返回 planId"""
    drop_plan()
    ids = {}
    for code, name, typ, _ in INDICATORS:
        mysql_exec(f"INSERT INTO {DB}.oa_perf_indicator "
                   f"(indicator_code, indicator_name, indicator_type, status) "
                   f"VALUES ('{code}', '{name}', {typ}, 1)")
        ids[code] = mysql(f"SELECT id FROM {DB}.oa_perf_indicator WHERE indicator_code='{code}'")
    mysql_exec(f"INSERT INTO {DB}.oa_perf_plan (plan_code, plan_name, cycle_type, score_mode, status) "
               f"VALUES ('{PLAN_CODE}', '导入校验用方案', 2, 1, 1)")
    plan_id = mysql(f"SELECT id FROM {DB}.oa_perf_plan WHERE plan_code='{PLAN_CODE}'")
    for sort, (code, _, _, weight) in enumerate(INDICATORS, start=1):
        mysql_exec(f"INSERT INTO {DB}.oa_perf_plan_indicator (plan_id, indicator_id, weight, sort) "
                   f"VALUES ({plan_id}, {ids[code]}, {weight}, {sort})")
    for code, name, lo, hi, coef, sort in GRADES:
        mysql_exec(f"INSERT INTO {DB}.oa_perf_plan_grade "
                   f"(plan_id, grade_code, grade_name, min_score, max_score, coefficient, sort) "
                   f"VALUES ({plan_id}, '{code}', '{name}', {lo}, {hi}, {coef}, {sort})")
    return plan_id


def cycle_id_of(code):
    return mysql(f"SELECT id FROM {DB}.oa_perf_cycle WHERE cycle_code='{code}' LIMIT 1")


def drop_cycle(code):
    cid = cycle_id_of(code)
    if not cid:
        return
    mysql_exec(f"DELETE i FROM {DB}.oa_perf_sheet_item i WHERE i.sheet_id IN "
               f"(SELECT id FROM {DB}.oa_perf_sheet WHERE cycle_id={cid})")
    mysql_exec(f"DELETE FROM {DB}.oa_perf_sheet WHERE cycle_id={cid}")
    mysql_exec(f"DELETE FROM {DB}.oa_perf_cycle WHERE id={cid}")


def ensure_cycle(plan_id, code, name, stage, status, start, end):
    drop_cycle(code)
    mysql_exec(f"INSERT INTO {DB}.oa_perf_cycle (cycle_code, cycle_name, plan_id, stage, status, "
               f"start_date, end_date) VALUES ('{code}', '{name}', {plan_id}, {stage}, {status}, "
               f"'{start}', '{end}')")
    return cycle_id_of(code)


def cleanup():
    drop_cycle(CYCLE_CODE)
    drop_cycle(ACTIVE_CODE)
    drop_plan()


def sheet_of(cycle_id, uid):
    rows = mysql(f"SELECT total_score, grade_code, coefficient, source, manager_status, "
                 f"confirm_status, self_total FROM {DB}.oa_perf_sheet "
                 f"WHERE cycle_id={cycle_id} AND user_id={uid} AND is_deleted=0")
    return rows.split("\t") if rows else []


def errors_of(body):
    return " ".join(e.get("message", "") for e in ((body.get("data") or {}).get("errors") or []))


def main():
    admin = token(1, "admin")
    cleanup()
    plan_id = ensure_plan()
    ck("自建方案（业绩100%+加/扣分项）", bool(plan_id), f"planId={plan_id}")
    cycle_id = ensure_cycle(plan_id, CYCLE_CODE, "导入校验用已归档周期", 6, 2,
                            "2026-09-01", "2026-09-30")
    ck("自建已归档周期", bool(cycle_id), f"cycleId={cycle_id}")
    import_url = f"/hr/perf/import?cycleId={cycle_id}"

    print("== 1. 模板：列由该周期方案的指标决定 ==")
    st, raw = req("GET", f"/hr/perf/import/template?cycleId={cycle_id}", tok=admin, raw=True)
    ck("模板下载成功", st == 200 and len(raw) > 1000, f"status={st} bytes={len(raw)}")
    wb = load_workbook(io.BytesIO(raw))
    header = [c.value for c in wb["绩效结果"][1]]
    ck("表头含工号/姓名", header[0] == "工号" and header[1] == "姓名", str(header[:2]))
    ck("指标列带权重后缀", COL_PERF in header, str(header))
    ck("表头含总分与评语", header[-2] == "总分" and header[-1] == "评语", str(header[-2:]))

    print("== 2. 导入：2 条成功 + 2 条失败（总分超限 / 工号不存在） ==")
    rows = [
        ("JSB022", "张明", 90, 5, None, 95, "历史台账"),
        ("JSB023", "李伟", 72, None, None, None, ""),
        ("JSB024", "王芳", 88, None, None, 130, ""),     # 总分超上限
        ("NO-SUCH-001", "", 80, None, None, None, ""),    # 工号不存在
    ]
    _, body = upload(import_url, "perf1.xlsx", xlsx(rows), tok=admin)
    data = body.get("data") or {}
    ck("导入统计：2 成功 2 失败", data.get("imported") == 2 and data.get("failed") == 2,
       f"imported={data.get('imported')} failed={data.get('failed')} errors={errors_of(body)}")
    msgs = errors_of(body)
    ck("总分 130 被拒且提示上限 120", "130" in msgs and "120" in msgs, msgs[:100])
    ck("不存在的工号被拒", "不存在" in msgs, msgs[:100])

    print("== 3. 落库口径：分数、等级、系数、来源 ==")
    zhang = sheet_of(cycle_id, 101808)
    ck("张明总分 95（90×100% + 5）", bool(zhang) and zhang[0].startswith("95"),
       f"total={zhang[0] if zhang else '-'}")
    ck("等级 A、系数 1.20（按方案映射）",
       len(zhang) > 2 and zhang[1] == "A" and zhang[2].startswith("1.2"),
       f"grade={zhang[1] if len(zhang) > 1 else '-'} coef={zhang[2] if len(zhang) > 2 else '-'}")
    ck("来源标为导入（source=2）", len(zhang) > 3 and zhang[3] == "2",
       f"source={zhang[3] if len(zhang) > 3 else '-'}")
    ck("五个状态置为已完成", len(zhang) > 5 and zhang[4] == "1" and zhang[5] == "1",
       f"manager={zhang[4] if len(zhang) > 4 else '-'} confirm={zhang[5] if len(zhang) > 5 else '-'}")
    ck("未编造自评分（selfTotal 为空）", len(zhang) > 6 and zhang[6] in ("", "NULL"),
       f"selfTotal={zhang[6] if len(zhang) > 6 else '-'}")

    li = sheet_of(cycle_id, 101809)
    ck("李伟 72 分 → C 等级、系数 0.80",
       len(li) > 2 and li[1] == "C" and li[2].startswith("0.8"),
       f"grade={li[1] if len(li) > 1 else '-'} coef={li[2] if len(li) > 2 else '-'}")

    item = mysql(f"SELECT manager_score, final_score, weight, indicator_type FROM {DB}.oa_perf_sheet_item "
                 f"WHERE sheet_id=(SELECT id FROM {DB}.oa_perf_sheet WHERE cycle_id={cycle_id} "
                 f"AND user_id=101808 AND is_deleted=0) ORDER BY indicator_type, id")
    first = item.splitlines()[0].split("\t") if item else []
    ck("指标行写入分数且权重复用方案值（100）",
       len(first) > 3 and first[0].startswith("90") and first[2].startswith("100"), str(first))

    print("== 4. 重复导入是覆盖而非重复建档 ==")
    _, body = upload(import_url, "perf1.xlsx", xlsx(rows), tok=admin)
    data = body.get("data") or {}
    ck("重导：覆盖 2 人、不新建", data.get("imported") == 2 and data.get("updated") == 2,
       f"imported={data.get('imported')} updated={data.get('updated')}")
    cnt = mysql(f"SELECT COUNT(*) FROM {DB}.oa_perf_sheet WHERE cycle_id={cycle_id} AND user_id=101808")
    ck("考核表仍只有一条", cnt == "1", f"count={cnt}")

    print("== 5. 漏列指标：权重合计不足被整份拒绝 ==")
    short_header = ["工号", "姓名", COL_BONUS, "总分", "评语"]
    _, body = upload(import_url, "short.xlsx",
                     xlsx([("JSB022", "张明", 5, None, "")], header=short_header), tok=admin)
    ck("权重合计不足 100% 被拒（6501）", body.get("code") == 6501,
       f"code={body.get('code')} msg={body.get('message')}")

    print("== 6. 进行中的周期不允许导入 ==")
    active_id = ensure_cycle(plan_id, ACTIVE_CODE, "导入校验用进行中周期", 2, 1,
                             "2026-10-01", "2027-03-31")
    ck("造出进行中周期", bool(active_id), f"cycleId={active_id}")
    _, body = upload(f"/hr/perf/import?cycleId={active_id}", "active.xlsx",
                     xlsx([("JSB022", "张明", 90, None, None, None, "")]), tok=admin)
    ck("进行中周期被拒（6515）", body.get("code") == 6515,
       f"code={body.get('code')} msg={body.get('message')}")
    drop_cycle(ACTIVE_CODE)

    print("== 7. 总分与明细不一致的行被拒 ==")
    _, body = upload(import_url, "mismatch.xlsx",
                     xlsx([("JSB022", "张明", 90, None, None, 100, "")]), tok=admin)
    data = body.get("data") or {}
    ck("总分 100 与明细 90 不一致 → 该行失败",
       data.get("failed") == 1 and "不一致" in errors_of(body), errors_of(body)[:100])

    print("== 8. 清理 ==")
    cleanup()
    left = mysql(f"SELECT COUNT(*) FROM {DB}.oa_perf_sheet WHERE cycle_id={cycle_id} "
                 f"AND user_id IN (101808,101809,101810)")
    ck("测试数据清理干净", left == "0", f"left={left}")

    return summary()


if __name__ == "__main__":
    sys.exit(main())
