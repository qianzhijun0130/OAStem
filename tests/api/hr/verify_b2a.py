#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""B2-A 验证：地方假别政策 CRUD + 假勤（请假）导出。"""
import sys

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
import xlib as X

DB = X.DB
adm = X.token(1, "admin")
staff = X.token(101809, "tech_staff1")   # 普通员工，无 HR 权限

CITY = "验证市"
YEAR = 2099   # 用未来年份隔离，避免污染真实配置

print("== 0. 清理 ==")
X.mysql(f"DELETE FROM {DB}.oa_leave_policy WHERE city='{CITY}'")

print("== 1. 可配置假别下拉 ==")
st, body = X.req("GET", "/hr/leave/policy/types", tok=adm)
types = body.get("data") or []
codes = [t.get("code") for t in types]
X.ck("假别下拉返回 4 项", st == 200 and len(types) == 4, f"types={codes}")
X.ck("只含政策类假别(4/5/6/7)", sorted(codes) == [4, 5, 6, 7], str(codes))
X.ck("标签为中文", any(t.get("label") == "婚假" for t in types), str([t.get('label') for t in types]))

print("== 2. 新增政策 ==")
st, body = X.req("POST", "/hr/leave/policy", body={
    "city": CITY, "leaveType": 4, "days": 3, "effectiveYear": YEAR, "remark": "验证用婚假",
}, tok=adm)
pid = body.get("data")
X.ck("新增婚假政策成功", st == 200 and body.get("code") == 200 and pid, f"code={body.get('code')} id={pid}")

print("== 3. 列表可见 ==")
st, body = X.req("GET", f"/hr/leave/policy?year={YEAR}", tok=adm)
rows = [r for r in (body.get("data") or []) if r.get("city") == CITY]
X.ck("列表可查到新增政策", len(rows) == 1, f"rows={len(rows)}")
if rows:
    X.ck("假别标签正确", rows[0].get("leaveTypeLabel") == "婚假", str(rows[0].get("leaveTypeLabel")))
    X.ck("天数正确", str(rows[0].get("days")) == "3.0" or str(rows[0].get("days")) == "3", str(rows[0].get("days")))

print("== 4. 唯一键冲突 ==")
st, body = X.req("POST", "/hr/leave/policy", body={
    "city": CITY, "leaveType": 4, "days": 5, "effectiveYear": YEAR,
}, tok=adm)
X.ck("同城市同假别同年度重复新增被拒(6311)", body.get("code") == 6311, f"code={body.get('code')} msg={body.get('message')}")

print("== 5. 修改 ==")
st, body = X.req("POST", "/hr/leave/policy", body={
    "id": pid, "city": CITY, "leaveType": 4, "days": 10, "effectiveYear": YEAR, "remark": "改为 10 天",
}, tok=adm)
X.ck("修改政策成功", st == 200 and body.get("code") == 200, f"code={body.get('code')}")
st, body = X.req("GET", f"/hr/leave/policy?year={YEAR}", tok=adm)
row = [r for r in (body.get("data") or []) if r.get("id") == pid]
X.ck("修改后天数生效", row and float(row[0].get("days")) == 10.0, str(row[0].get('days')) if row else "none")

print("== 6. 非法假别被拒 ==")
st, body = X.req("POST", "/hr/leave/policy", body={
    "city": CITY, "leaveType": 1, "days": 3, "effectiveYear": YEAR,
}, tok=adm)
X.ck("事假不可配置地方政策", body.get("code") == 400, f"code={body.get('code')} msg={body.get('message')}")

print("== 7. 权限：普通员工不可维护 ==")
st, body = X.req("POST", "/hr/leave/policy", body={
    "city": CITY, "leaveType": 5, "days": 158, "effectiveYear": YEAR,
}, tok=staff)
X.ck("员工新增政策被拒(403)", body.get("code") == 403, f"code={body.get('code')}")

print("== 8. 删除 ==")
st, body = X.req("DELETE", f"/hr/leave/policy/{pid}", tok=adm)
X.ck("删除政策成功", st == 200 and body.get("code") == 200, f"code={body.get('code')}")
st, body = X.req("GET", f"/hr/leave/policy?year={YEAR}", tok=adm)
X.ck("删除后列表不再显示", not [r for r in (body.get("data") or []) if r.get("id") == pid])

print("== 9. 请假导出 ==")
st, raw = X.req("GET", "/hr/leave/export?month=2026-10", tok=adm, raw=True)
X.ck("导出返回 200", st == 200, f"st={st}")
X.ck("导出为 xlsx（Zip 魔数 PK）", raw[:2] == b"PK", f"head={raw[:4]!r}")
X.ck("导出非空", len(raw) > 2000, f"bytes={len(raw)}")

print("== 10. 导出权限 ==")
st, body = X.req("GET", "/hr/leave/export", tok=staff)
X.ck("员工导出被拒(403)", (body.get("code") if isinstance(body, dict) else None) == 403,
     f"st={st} body={str(body)[:60]}")

print("== 11. 数据还原 ==")
X.mysql(f"DELETE FROM {DB}.oa_leave_policy WHERE city='{CITY}'")
left = X.mysql(f"SELECT COUNT(*) FROM {DB}.oa_leave_policy WHERE city='{CITY}'")
X.ck("测试数据已清理", left == "0", f"left={left}")

sys.exit(X.summary())
