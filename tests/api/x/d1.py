#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""D1 修复验证：可清空列（flow_config / icon / category / remark / node_config）能真正清空，
清空条件时引擎侧的行为边界，以及「保存失败不得先删掉线上部署」。

用 leave_apply（唯一带 flow_config 的模板）做靶子，结束时按备份还原库与引擎。
"""
import sys

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, ck, mysql, req, summary, token  # noqa: E402

T = token(1, "admin")
FLOW_ID = 198
FLOW_CODE = "leave_apply"


def cond_count(deployment_id):
    if not deployment_id:
        return 0
    sql = (f"SELECT IFNULL(SUM((LENGTH(CONVERT(BYTES_ USING utf8mb4)) - "
           f"LENGTH(REPLACE(CONVERT(BYTES_ USING utf8mb4),'conditionExpression',''))) "
           f"DIV LENGTH('conditionExpression')),0) FROM {DB}.act_ge_bytearray "
           f"WHERE DEPLOYMENT_ID_='{deployment_id}' AND NAME_ LIKE '%bpmn%'")
    return int(mysql(sql) or 0)


def col(field):
    return mysql(f"SELECT IFNULL({field},'<NULL>') FROM {DB}.oa_flow_definition WHERE id={FLOW_ID}")


def dep():
    return mysql(f"SELECT deployment_id FROM {DB}.oa_flow_definition WHERE id={FLOW_ID}")


def main():
    _st, detail = req("GET", f"/workflow/flow-definition/{FLOW_ID}", tok=T)
    d = detail.get("data") or {}
    backup = {k: d.get(k) for k in ("flowConfig", "nodeConfig", "icon", "category", "remark", "status")}
    dep0, ut0 = dep(), mysql(f"SELECT update_time FROM {DB}.oa_flow_definition WHERE id={FLOW_ID}")
    cond0 = cond_count(dep0)
    _st, bpmn = req("GET", f"/workflow/flow-definition/{FLOW_ID}/bpmn", tok=T)
    xml0 = bpmn.get("data") or ""
    name = d.get("flowName")
    ck("前置：模板含 flow_config，部署资源含注入的条件表达式",
       bool(backup["flowConfig"]) and cond0 > 0, f"fc_len={len(backup['flowConfig'] or '')} cond={cond0}")

    # A) 只改库（不传 bpmnXml）：可清空列全部变 NULL，引擎侧不动
    st, b = req("POST", "/workflow/flow-definition",
                {"id": FLOW_ID, "flowCode": FLOW_CODE, "flowName": name, "status": backup["status"],
                 "flowConfig": None, "nodeConfig": None, "icon": None, "category": None, "remark": None}, tok=T)
    ck("D1-1 不带 bpmnXml 保存成功", st == 200 and b.get("code") == 200,
       f"http={st} code={b.get('code')}")
    ck("D1-2 flow_config 真正被清空（列 IS NULL，不再保留旧值）", col("flow_config") == "<NULL>",
       str(col('flow_config'))[:40])
    ck("D1-3 icon / category / remark 同步清空",
       col("icon") == "<NULL>" and col("category") == "<NULL>" and col("remark") == "<NULL>",
       f"icon={col('icon')} cat={col('category')} remark={col('remark')}")
    ck("D1-4 node_config 同为可清空列，一并清空", col("node_config") == "<NULL>", str(col('node_config'))[:30])
    ck("D1-5 未重新部署：部署号与引擎内条件均未变（清库不惊动在跑版本）",
       dep() == dep0 and cond_count(dep0) == cond0, f"dep_changed={dep() != dep0}")
    ut1 = mysql(f"SELECT update_time FROM {DB}.oa_flow_definition WHERE id={FLOW_ID}")
    ck("D1-6 update_time 随保存刷新（wrapper 更新未漏填审计字段）", ut1 > ut0, f"{ut0} → {ut1}")

    # B) 空串/空白视同清空
    st, b = req("POST", "/workflow/flow-definition",
                {"id": FLOW_ID, "flowCode": FLOW_CODE, "flowName": name, "status": backup["status"],
                 "flowConfig": "", "icon": "   ", "category": "", "remark": " "}, tok=T)
    ck("D1-7 空串/空白归一为 NULL（库里不会同时存在 '' 与 NULL 两种「未配置」）",
       b.get("code") == 200 and col("flow_config") == "<NULL>" and col("icon") == "<NULL>"
       and col("category") == "<NULL>" and col("remark") == "<NULL>", f"code={b.get('code')}")

    # C) 还原：原始配置 + 原始 XML 重新部署，条件重新注入
    st, b = req("POST", "/workflow/flow-definition",
                {"id": FLOW_ID, "flowCode": FLOW_CODE, "flowName": name, "status": backup["status"],
                 "category": backup["category"], "icon": backup["icon"], "remark": backup["remark"],
                 "bpmnXml": xml0, "flowConfig": backup["flowConfig"], "nodeConfig": backup["nodeConfig"]}, tok=T)
    dep1 = dep()
    ck("D1-8 还原成功且条件重新注入（条数与基线一致）",
       b.get("code") == 200 and cond_count(dep1) == cond0, f"code={b.get('code')} cond={cond_count(dep1)} 基线={cond0}")
    ck("D1-9 还原后各列与备份逐字一致",
       col("flow_config") == str(backup["flowConfig"]) and col("node_config") == str(backup["nodeConfig"])
       and col("icon") == str(backup["icon"]) and col("category") == str(backup["category"]),
       f"icon={col('icon')} cat={col('category')} fc_match={col('flow_config') == str(backup['flowConfig'])}")

    # D) 真实 UI 序列：清空条件与 bpmnXml 同一请求提交 → 结构校验拒绝，且线上部署必须还在
    st, b = req("POST", "/workflow/flow-definition",
                {"id": FLOW_ID, "flowCode": FLOW_CODE, "flowName": name, "status": backup["status"],
                 "bpmnXml": xml0, "flowConfig": None}, tok=T)
    ck("D1-10 清空条件且画布仍有多分支网关 → 3005 明确拒绝（不再静默沿用旧条件）",
       b.get("code") == 3005, f"code={b.get('code')} msg={str(b.get('message'))[:50]}")
    ck("D1-11 保存失败不删除线上部署（校验先于卸载），库也未半截写入",
       dep() == dep1 and col("flow_config") == str(backup["flowConfig"]),
       f"dep_changed={dep() != dep1} fc_kept={col('flow_config') != '<NULL>'}")

    # E) 收尾核对：部署号、条件数、各列
    ck("D1-12 收尾状态与基线等价（部署号可不同，条件数/字段一致）",
       dep() == dep1 and cond_count(dep1) == cond0 and col("flow_config") == str(backup["flowConfig"]),
       f"dep={dep()[:8]} cond={cond_count(dep1)}")

    print(f"\n基线 dep={dep0[:8]} cond={cond0}　当前 dep={dep1[:8]} cond={cond_count(dep1)}")
    return summary()


sys.exit(main())
