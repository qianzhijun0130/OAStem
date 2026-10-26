#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""薪酬（P1-1）接口验证。

覆盖：薪资项 / 社保政策 / 薪资档案分段 / 核算前置校验 / 金额口径（21.75、社保、累计预扣个税）
/ 手工调整留痕 / 状态机 review→lock→pay / 工资条可见性 / 权限。

金额断言用手算值对照，不做「大概对」的判断——薪资算错 0.01 就是事故。
"""
import sys

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import ck, mysql, req, summary, token  # noqa: E402

MONTH = "2026-01"
DEPT = 2
TECH = [101808, 101809, 101810]
MAIN_USER = 101809

STATE = {"recordIds": []}


def cleanup():
    """按测试标记清理，保留其它数据"""
    mysql(f"DELETE FROM oa_system.oa_salary_adjust_log WHERE record_id IN "
          f"(SELECT id FROM oa_system.oa_salary_record WHERE month='{MONTH}')")
    mysql(f"DELETE FROM oa_system.oa_salary_record_item WHERE record_id IN "
          f"(SELECT id FROM oa_system.oa_salary_record WHERE month='{MONTH}')")
    mysql(f"DELETE FROM oa_system.oa_salary_record WHERE month='{MONTH}'")
    mysql(f"DELETE FROM oa_system.oa_salary_profile WHERE user_id IN ({','.join(map(str, TECH))})")
    mysql("DELETE FROM oa_system.oa_salary_item WHERE item_code='TEST_ITEM'")
    mysql("DELETE FROM oa_system.oa_social_policy WHERE city='测试市'")
    mysql(f"DELETE FROM oa_system.oa_attendance_month WHERE month='{MONTH}'")


def main():
    cleanup()
    admin = token(1, "admin", hours=2)

    # ---------------- 1. 薪资项 ----------------
    code, body = req("GET", "/hr/salary/items", tok=admin)
    items = body.get("data") or []
    ck("内置薪资项 11 个", body.get("code") == 200 and len(items) == 11, f"code={body.get('code')} n={len(items)}")
    ck("内置项含「基本工资 / 个人所得税」",
       any(i["itemCode"] == "BASE" for i in items) and any(i["itemCode"] == "TAX" for i in items), "")

    # ---------------- 2. 社保政策 ----------------
    code, body = req("GET", "/hr/salary/policies?year=2026", tok=admin)
    policies = body.get("data") or []
    ck("默认社保政策已初始化（2026）", len(policies) >= 1, f"n={len(policies)}")

    code, body = req("POST", "/hr/salary/policies", {
        "city": "测试市", "policyYear": 2026, "baseMin": 3000, "baseMax": 30000,
        "fundBaseMin": 2000, "fundBaseMax": 30000,
        "pensionPersonal": 0.08, "pensionCompany": 0.16,
        "medicalPersonal": 0.02, "medicalCompany": 0.098,
        "unemploymentPersonal": 0.005, "unemploymentCompany": 0.005,
        "injuryCompany": 0.002, "maternityCompany": 0.008,
        "fundPersonal": 0.12, "fundCompany": 0.12, "remark": "自动化测试",
    }, tok=admin)
    ck("新增社保政策成功", body.get("code") == 200 and body.get("data"), f"code={body.get('code')}")

    code, body = req("POST", "/hr/salary/policies", {
        "city": "测试市", "policyYear": 2026, "baseMin": 3000, "baseMax": 30000,
    }, tok=admin)
    ck("同城市同年度重复 → 6407", body.get("code") == 6407, f"code={body.get('code')}")

    # ---------------- 3. 薪资档案（分段生效） ----------------
    for uid in TECH:
        code, body = req("POST", "/hr/salary/profiles", {
            "userId": uid, "effectiveDate": "2026-01-01",
            "baseSalary": 8000, "postSalary": 2000, "perfSalary": 0, "allowance": 500,
            "city": "默认", "specialDeduct": 0, "changeReason": "自动化测试-初始档案",
        }, tok=admin)
        assert body.get("code") == 200, body
    ck("三人薪资档案建立", True, "")

    code, body = req("POST", "/hr/salary/profiles", {
        "userId": MAIN_USER, "effectiveDate": "2026-01-01",
        "baseSalary": 8000, "postSalary": 2000, "allowance": 500,
    }, tok=admin)
    ck("同人生效日期重复 → 400", body.get("code") == 400, f"code={body.get('code')} msg={body.get('message')}")

    # 调薪：新增更晚一段，历史不动
    code, body = req("POST", "/hr/salary/profiles", {
        "userId": MAIN_USER, "effectiveDate": "2026-06-01",
        "baseSalary": 9000, "postSalary": 2500, "perfSalary": 0, "allowance": 500,
        "changeReason": "自动化测试-调薪",
    }, tok=admin)
    ck("调薪（新增更晚生效段）成功", body.get("code") == 200, f"code={body.get('code')}")

    code, body = req("GET", f"/hr/salary/profiles/{MAIN_USER}/history", tok=admin)
    history = body.get("data") or []
    ck("档案历史 2 段且按生效日倒序",
       len(history) == 2 and history[0]["effectiveDate"] == "2026-06-01"
       and float(history[0]["standardSalary"]) == 12000.0,
       f"n={len(history)} first={history[0]['effectiveDate'] if history else None}")

    # ---------------- 4. 核算前置：考勤未封账 → 6402 ----------------
    # BIZ-OPT-04 之后：核算要求范围内**每个**员工都有考勤月结，缺一个就整批阻断，
    # 因此这里给全部门都造月结（原先只造主测试用户，会被"缺少考勤汇总"拦下）
    dept_users = [int(x) for x in mysql(
        f"SELECT id FROM oa_system.oa_user WHERE is_deleted=0 AND dept_id={DEPT} "
        f"AND employment_status IN (2,3,4)").split()]
    for uid in dept_users:
        mysql(f"INSERT INTO oa_system.oa_attendance_month (user_id, month, should_days, actual_days, status) "
              f"VALUES ({uid}, '{MONTH}', 21, 21, 1)")
    code, body = req("POST", f"/hr/salary/calc?month={MONTH}&deptId={DEPT}", tok=admin)
    ck("考勤未封账时拒绝核算 → 6402", body.get("code") == 6402,
       f"code={body.get('code')} msg={body.get('message')}")

    mysql(f"UPDATE oa_system.oa_attendance_month SET status=2 WHERE month='{MONTH}'")

    # ---------------- 5. 核算与金额口径 ----------------
    code, body = req("POST", f"/hr/salary/calc?month={MONTH}&deptId={DEPT}", tok=admin)
    ck("核算成功（3 人）", body.get("code") == 200 and body.get("data") == 3,
       f"code={body.get('code')} n={body.get('data')} msg={body.get('message')}")

    code, body = req("GET", f"/hr/salary/records?month={MONTH}&deptId={DEPT}&pageNo=1&pageSize=20", tok=admin)
    rows = (body.get("data") or {}).get("records") or []
    mine = next((r for r in rows if r["userId"] == MAIN_USER), None)
    ck("核算列表返回 3 条", len(rows) == 3, f"n={len(rows)}")
    assert mine, "未找到主测试用户的核算记录"
    STATE["recordIds"].append(mine["id"])

    # 手算：standard=10500，社保基数 10500（未越界）
    # 个人 10500*(8%+2%+0.5%)=1102.50；公积金 10500*12%=1260.00
    # 累计应纳税所得额 = 10500 - 5000*1 - 2362.50 = 3137.50 → 税 3137.50*3% = 94.125 → 94.13
    # 实发 = 10500 - 1102.50 - 1260 - 94.13 = 8043.37
    ck("应发 = 基本+岗位+津贴 = 10500", float(mine["grossPay"]) == 10500.0, str(mine["grossPay"]))
    ck("社保个人 = 1102.50（10500×10.5%）", float(mine["socialPersonal"]) == 1102.50, str(mine["socialPersonal"]))
    ck("公积金个人 = 1260.00（10500×12%）", float(mine["fundPersonal"]) == 1260.00, str(mine["fundPersonal"]))
    ck("个税 = 94.13（累计预扣，3137.50×3%）", float(mine["tax"]) == 94.13, str(mine["tax"]))
    ck("实发 = 8043.37", float(mine["netPay"]) == 8043.37, str(mine["netPay"]))

    code, detail = req("GET", f"/hr/salary/records/{mine['id']}", tok=admin)
    d = detail.get("data") or {}
    ck("明细逐项：基本/岗位/津贴/社保/公积金/个税 6 行",
       len(d.get("items") or []) == 6,
       f"items={[i['itemName'] for i in (d.get('items') or [])]}")
    ck("明细金额与主表一致",
       all(any(i["itemCode"] == code_ and float(i["amount"]) == expect
               for i in (d.get("items") or []))
           for code_, expect in [("BASE", 8000.0), ("POST", 2000.0), ("ALLOW", 500.0),
                                 ("SOCIAL", 1102.5), ("FUND", 1260.0), ("TAX", 94.13)]),
       f"items={[(i['itemCode'], float(i['amount'])) for i in (d.get('items') or [])]}")
    ck("单位成本 = 社保 2866.50 + 公积金 1260 = 4126.50",
       float(mine["companyCost"]) == 4126.50, str(mine["companyCost"]))

    # ---------------- 6. 手工调整（留痕 + 联动重算） ----------------
    code, body = req("PUT", f"/hr/salary/records/{mine['id']}/adjust",
                     {"otherIncome": 1000, "remark": ""}, tok=admin)
    ck("调整不填原因 → 6405", body.get("code") == 6405, f"code={body.get('code')}")

    code, body = req("PUT", f"/hr/salary/records/{mine['id']}/adjust",
                     {"otherIncome": 1000, "remark": "自动化测试-补发奖金"}, tok=admin)
    ck("调整成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")

    code, detail = req("GET", f"/hr/salary/records/{mine['id']}", tok=admin)
    d = detail.get("data") or {}
    # 应发 11500；累计应纳税所得额 4137.50 → 税 124.13；实发 11500-1102.5-1260-124.13 = 9013.37
    ck("调整后应发 = 11500", float(d.get("grossPay") or 0) == 11500.0, str(d.get("grossPay")))
    ck("调整后个税联动为 124.13", float(d.get("tax") or 0) == 124.13, str(d.get("tax")))
    ck("调整后实发 = 9013.37", float(d.get("netPay") or 0) == 9013.37, str(d.get("netPay")))
    logs = mysql(f"SELECT COUNT(*) FROM oa_system.oa_salary_adjust_log WHERE record_id={mine['id']}").strip()
    ck("写入手工调整留痕 1 条", logs == "1", f"n={logs}")

    # ---------------- 7. 状态机 ----------------
    code, body = req("POST", f"/hr/salary/review?month={MONTH}&deptId={DEPT}", tok=admin)
    ck("复核：草稿→已复核（3 条）", body.get("code") == 200 and body.get("data") == 3,
       f"code={body.get('code')} n={body.get('data')}")

    code, body = req("POST", f"/hr/salary/calc?month={MONTH}&deptId={DEPT}", tok=admin)
    ck("再次核算不覆盖已复核（0 条）", body.get("data") == 0, f"n={body.get('data')}")

    code, body = req("POST", f"/hr/salary/lock?month={MONTH}&deptId={DEPT}", tok=admin)
    ck("封账：已复核→已封账", body.get("code") == 200 and body.get("data") == 3, f"n={body.get('data')}")

    code, body = req("PUT", f"/hr/salary/records/{mine['id']}/adjust",
                     {"otherIncome": 2000, "remark": "封账后调整"}, tok=admin)
    ck("封账后调整 → 6403", body.get("code") == 6403, f"code={body.get('code')}")

    code, body = req("POST", f"/hr/salary/pay?month={MONTH}&deptId={DEPT}", tok=admin)
    ck("发放：已封账→已发放", body.get("code") == 200 and body.get("data") == 3, f"n={body.get('data')}")

    code, body = req("POST", f"/hr/salary/pay?month={MONTH}&deptId={DEPT}", tok=admin)
    ck("重复发放 → 6404", body.get("code") == 6404, f"code={body.get('code')}")

    # ---------------- 8. 工资条（本人） ----------------
    staff = token(MAIN_USER, "tech_staff1", hours=2)
    code, body = req("GET", "/hr/salary/my?year=2026", tok=staff)
    slips = body.get("data") or []
    ck("本人可见 1 条工资条", len(slips) == 1 and slips[0]["month"] == MONTH, f"n={len(slips)}")
    ck("工资条含逐项明细", len((slips[0].get("items") or [])) >= 5 if slips else False,
       f"items={len(slips[0].get('items') or []) if slips else 0}")

    # 别人的工资条：按 recordId 取应被权限拦住（service 未按人校验 → 由权限码拦截）
    code, body = req("GET", f"/hr/salary/records/{mine['id']}", tok=staff)
    ck("普通员工访问核算明细被拒（403）", body.get("code") == 403, f"code={body.get('code')}")

    code, body = req("GET", "/hr/salary/my/2025-12", tok=staff)
    ck("无数据月份 → 6408", body.get("code") == 6408, f"code={body.get('code')}")

    # ---------------- 9. 无档案员工 → 6401 ----------------
    mysql(f"DELETE FROM oa_system.oa_salary_profile WHERE user_id={TECH[0]}")
    code, body = req("POST", f"/hr/salary/calc?month=2026-02&deptId={DEPT}", tok=admin)
    ck("存在无档案员工 → 6401 并给出姓名", body.get("code") == 6401 and "张明" in (body.get("message") or ""),
       f"code={body.get('code')} msg={(body.get('message') or '')[:60]}")
    mysql("DELETE FROM oa_system.oa_salary_record WHERE month='2026-02'")

    # ---------------- 收尾 ----------------
    cleanup()
    return summary()


if __name__ == "__main__":
    sys.exit(main())
