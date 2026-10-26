#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""阶段四验证：挂起/恢复 + 超时（due_time/overdue）。

覆盖：
  A 发起后实例带 dueTime（全局默认 48h）且 overdue=false
  B 候选人待办可见 → 管理员挂起 → 状态 7、待办消失
  C 重复挂起 → 3107
  D 恢复 → 状态 2、待办回来
  E 未挂起时恢复 → 3108
  F 对已结束（已通过）实例挂起 → 3106
  G 无 suspend 权限 → 403
  H 把 due_time 改成过去 → overdue=true（列表与详情一致）
"""
import base64, hashlib, hmac, json, os, subprocess, sys, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:8080"
PROPS = [Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application.properties"),
         Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application-local.properties")]
PERMS = Path(r"F:/GitHub/OAStem/tests/.runtime/oastem_perms.txt")
MYSQL = r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe"
DB, FLOW = "oa_system", "t_phase4"
RESULTS = []


def lp():
    d = {}
    for p in PROPS:
        if p.exists():
            for l in p.read_text(encoding="utf-8").splitlines():
                l = l.strip()
                if l and not l.startswith("#") and "=" in l:
                    k, v = l.split("=", 1); d[k.strip()] = v.strip()
    return d


PD = lp()
SEC = PD["jwt.secret"].encode()
ALL: list[str] = []  # 从 oa_menu 动态加载（权限码会随阶段四新增而变化，静态快照会过期）
U = PD.get("spring.datasource.username", "root")
U = U.split(":")[-1].rstrip("}") if U.startswith("${") else U
PW = PD.get("spring.datasource.password", "")


def mysql(sql):
    return subprocess.run([MYSQL, "--force", f"-u{U}", "-N", "-e", sql],
                          capture_output=True, text=True, env=dict(os.environ, MYSQL_PWD=PW)).stdout.strip()


def scalar(sql):
    o = mysql(sql)
    return o.splitlines()[0] if o else ""


def b64(d): return base64.urlsafe_b64encode(d).rstrip(b"=").decode()


def tok(uid, un, roles, perms):
    now = int(time.time())
    p = {"sub": un, "userId": uid, "roles": roles, "perms": perms, "iat": now, "exp": now + 3600}
    h = b64(json.dumps({"alg": "HS384", "typ": "JWT"}, separators=(",", ":")).encode())
    b = b64(json.dumps(p, separators=(",", ":")).encode())
    return f"{h}.{b}.{b64(hmac.new(SEC, f'{h}.{b}'.encode(), hashlib.sha384).digest())}"


ALL = [x for x in mysql(f"SELECT menu_code FROM {DB}.oa_menu WHERE menu_code IS NOT NULL AND menu_code <> ''").splitlines() if x]
print(f"perms from db = {len(ALL)}")
T_ADMIN = tok(1, "admin", ["SUPER_ADMIN"], ALL)
T_APPL = tok(101809, "tech_staff1", ["TECH_MANAGER"], ALL)
T_APPROVER = tok(101808, "tech_admin", ["TECH_MANAGER"], ALL)
T_NO_SUSPEND = tok(1, "admin", ["TECH_MANAGER"],
                   [c for c in ALL if not c.startswith("system:approval:suspend")
                    and not c.startswith("system:approval:resume")])


def req(method, path, t=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    if t:
        r.add_header("Authorization", "Bearer " + t)
    try:
        with urllib.request.urlopen(r) as x: return json.loads(x.read().decode())
    except urllib.error.HTTPError as e:
        try: return json.loads(e.read().decode())
        except Exception: return {}


def check(n, ok, d=""):
    RESULTS.append((n, ok)); print(f"{'PASS' if ok else 'FAIL'} | {n} | {d}")


def bpmn(pid):
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://oastem.com/bpmn">
  <process id="{pid}" name="{pid}" isExecutable="true">
    <startEvent id="startEvent" name="开始"/>
    <userTask id="approve" name="审批"/>
    <endEvent id="endEvent" name="结束"/>
    <sequenceFlow id="f1" sourceRef="startEvent" targetRef="approve"/>
    <sequenceFlow id="f2" sourceRef="approve" targetRef="endEvent"/>
  </process>
</definitions>'''


def todo_has(inst_id, t):
    r = req("GET", "/workflow/approval/todo?pageNo=1&pageSize=100", t)
    return any(x.get("instanceId") == inst_id for x in (r.get("data") or {}).get("records", []))


def cleanup(inst_id):
    pid = scalar(f"SELECT proc_inst_id FROM {DB}.oa_approval_instance WHERE id={inst_id}") if inst_id else ""
    pin = f"'{pid}'" if pid else "''"
    mysql(" ".join(["SET FOREIGN_KEY_CHECKS=0;",
                    f"DELETE FROM {DB}.oa_approval_record WHERE instance_id={inst_id};" if inst_id else "SELECT 1;",
                    f"DELETE FROM {DB}.oa_approval_instance WHERE id={inst_id};" if inst_id else "SELECT 1;",
                    f"DELETE FROM {DB}.act_ru_variable WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_ru_task WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_ru_identitylink WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_ru_execution WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_hi_varinst WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_hi_detail WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_hi_identitylink WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_hi_taskinst WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_hi_actinst WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_hi_procinst WHERE PROC_INST_ID_={pin};",
                    f"DELETE FROM {DB}.act_re_procdef WHERE KEY_='{FLOW}';",
                    f"DELETE FROM {DB}.act_re_deployment WHERE ID_ NOT IN (SELECT DEPLOYMENT_ID_ FROM {DB}.act_re_procdef);",
                    f"DELETE FROM {DB}.act_ge_bytearray WHERE DEPLOYMENT_ID_ NOT IN (SELECT ID_ FROM {DB}.act_re_deployment);",
                    f"DELETE FROM {DB}.oa_flow_definition WHERE flow_code='{FLOW}';",
                    "SET FOREIGN_KEY_CHECKS=1;"]))


def main():
    mysql(f"DELETE FROM {DB}.oa_flow_definition WHERE flow_code='{FLOW}';")
    r = req("POST", "/workflow/flow-definition", T_ADMIN, {
        "flowCode": FLOW, "flowName": "阶段四验证", "category": "测试", "status": 1,
        "bpmnXml": bpmn(FLOW),
        "nodeConfig": json.dumps([{"nodeId": "approve", "nodeName": "审批",
                                   "assigneeType": 1, "assigneeValue": "101808"}])})
    assert r.get("code") == 200, r
    s = req("POST", "/workflow/approval/start", T_APPL,
            {"flowCode": FLOW, "title": "阶段四验证单", "formData": "{}"})
    assert s.get("code") == 200, s
    inst = s["data"]
    try:
        d = req("GET", f"/workflow/approval/{inst}/detail", T_ADMIN)
        instvo = (d.get("data") or {}).get("instance") or {}
        check("A 新实例带 dueTime 且 overdue=false",
              d.get("code") == 200 and bool(instvo.get("dueTime")) and instvo.get("overdue") is False,
              f"dueTime={instvo.get('dueTime')} overdue={instvo.get('overdue')}")

        check("B1 挂起前：候选人待办可见", todo_has(inst, T_APPROVER), "")
        sp = req("POST", f"/workflow/approval/suspend?instanceId={inst}", T_ADMIN)
        status = scalar(f"SELECT status FROM {DB}.oa_approval_instance WHERE id={inst}")
        check("B2 挂起 → 200 且 status=7", sp.get("code") == 200 and status == "7",
              f"code={sp.get('code')} status={status}")
        check("B3 挂起后：候选人待办消失", not todo_has(inst, T_APPROVER), "")

        sp2 = req("POST", f"/workflow/approval/suspend?instanceId={inst}", T_ADMIN)
        check("C 重复挂起 → 3107", sp2.get("code") == 3107, f"code={sp2.get('code')}")

        rs = req("POST", f"/workflow/approval/resume?instanceId={inst}", T_ADMIN)
        status2 = scalar(f"SELECT status FROM {DB}.oa_approval_instance WHERE id={inst}")
        check("D1 恢复 → 200 且 status=2", rs.get("code") == 200 and status2 == "2",
              f"code={rs.get('code')} status={status2}")
        check("D2 恢复后：候选人待办回来", todo_has(inst, T_APPROVER), "")

        rs2 = req("POST", f"/workflow/approval/resume?instanceId={inst}", T_ADMIN)
        check("E 未挂起时恢复 → 3108", rs2.get("code") == 3108, f"code={rs2.get('code')}")

        # F 对已结束实例挂起 → 3106（先用超管审批完成）
        tid = None
        dd = req("GET", f"/workflow/approval/{inst}/detail", T_ADMIN)
        tid = ((dd.get("data") or {}).get("currentTask") or {}).get("taskId")
        req("POST", "/workflow/approval/complete", T_ADMIN,
            {"taskId": tid, "action": "approve", "opinion": "阶段四完成"})
        done_status = scalar(f"SELECT status FROM {DB}.oa_approval_instance WHERE id={inst}")
        sp3 = req("POST", f"/workflow/approval/suspend?instanceId={inst}", T_ADMIN)
        check("F 对已通过实例挂起 → 3106", sp3.get("code") == 3106 and done_status == "3",
              f"code={sp3.get('code')} status={done_status}")

        perm = req("POST", f"/workflow/approval/suspend?instanceId={inst}", T_NO_SUSPEND)
        check("G 无 suspend 权限 → 403", perm.get("code") == 403, f"code={perm.get('code')}")
    finally:
        cleanup(inst)
        print("CLEANUP 残留实例=", scalar(f"SELECT COUNT(*) FROM {DB}.oa_approval_instance WHERE id={inst}"),
              "残留模板=", scalar(f"SELECT COUNT(*) FROM {DB}.oa_flow_definition WHERE flow_code='{FLOW}'"))

    # H 逾期标记：新建一个运行中实例，把 due_time 改成过去
    r2 = req("POST", "/workflow/flow-definition", T_ADMIN, {
        "flowCode": FLOW + "b", "flowName": "阶段四验证B", "category": "测试", "status": 1,
        "bpmnXml": bpmn(FLOW + "b"),
        "nodeConfig": json.dumps([{"nodeId": "approve", "nodeName": "审批",
                                   "assigneeType": 1, "assigneeValue": "101808"}])})
    inst2 = None
    try:
        s2 = req("POST", "/workflow/approval/start", T_APPL,
                 {"flowCode": FLOW + "b", "title": "阶段四逾期验证", "formData": "{}"})
        inst2 = s2.get("data")
        mysql(f"UPDATE {DB}.oa_approval_instance SET due_time = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE id={inst2};")
        d2 = req("GET", f"/workflow/approval/{inst2}/detail", T_ADMIN)
        vo = (d2.get("data") or {}).get("instance") or {}
        p2 = req("GET", "/workflow/approval/page?pageSize=50&title=" + urllib.parse.quote("阶段四逾期验证"), T_ADMIN)
        rec = next((x for x in (p2.get("data") or {}).get("records", []) if x.get("id") == inst2), {})
        check("H 过了截止时间 → overdue=true（详情与列表一致）",
              vo.get("overdue") is True and rec.get("overdue") is True,
              f"detail={vo.get('overdue')} list={rec.get('overdue')} due={vo.get('dueTime')}")
    finally:
        cleanup(inst2)
        mysql(f"DELETE FROM {DB}.oa_flow_definition WHERE flow_code='{FLOW}b';")
        print("CLEANUP2 残留=", scalar(f"SELECT COUNT(*) FROM {DB}.oa_approval_instance WHERE id={inst2}"))

    fail = [n for n, ok in RESULTS if not ok]
    print(f"\nSUMMARY: {len(RESULTS) - len(fail)}/{len(RESULTS)} 通过")
    return 0 if not fail else 1


if __name__ == "__main__":
    sys.exit(main())
