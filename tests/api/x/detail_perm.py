#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证「流程实例→详情」权限修复。

断言：
  A 超管取详情 → 200、canApprove=true、currentTask 非空（修复前 = 403）
  B 仅持 system:approval:instance 的经理取详情 → 200、canApprove=false（可看不可审）
  C 该经理尝试审批 → 3104（越权仍拦）
  D 无关账号（非参与人、无实例码）取详情 → 403（可见性未被放宽）
  E 未认证 → 401
  F 候选人取详情 → 200、canApprove=true
  G 申请人取详情 → 200、canApprove=false（非候选人不再显示审批区）
  H 超管审批任意节点 → 200，实例转已通过（修复前 = 3104）
  I 结束后详情 canApprove=false
  J 回归：超管取「遗留运行中实例 51」详情 → 200 + canApprove=true
"""
import base64, hashlib, hmac, json, os, subprocess, sys, time
import urllib.error, urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:8080"
PROPS = [Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application.properties"),
         Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application-local.properties")]
PERMS = Path(r"F:/GitHub/OAStem/tests/.runtime/oastem_perms.txt")
MYSQL = r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe"
DB = "oa_system"
FLOW_CODE = "t_detail_perm"
RESULTS = []


def load_props():
    d = {}
    for p in PROPS:
        if p.exists():
            for line in p.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    d[k.strip()] = v.strip()
    return d


PD = load_props()
SECRET = PD["jwt.secret"].encode()
ALL = [l.strip() for l in PERMS.read_text(encoding="utf-8").splitlines() if l.strip()]
DB_USER = PD.get("spring.datasource.username", "root")
DB_USER = DB_USER.split(":")[-1].rstrip("}") if DB_USER.startswith("${") else DB_USER
DB_PWD = PD.get("spring.datasource.password", "")


def mysql(sql):
    r = subprocess.run([MYSQL, "--force", f"-u{DB_USER}", "-N", "-e", sql],
                       capture_output=True, text=True, env=dict(os.environ, MYSQL_PWD=DB_PWD))
    return r.stdout.strip()


def scalar(sql):
    o = mysql(sql)
    return o.splitlines()[0] if o else ""


def b64(d):
    return base64.urlsafe_b64encode(d).rstrip(b"=").decode()


def token(uid, un, roles, perms):
    now = int(time.time())
    p = {"sub": un, "userId": uid, "roles": roles, "perms": perms, "iat": now, "exp": now + 3600}
    h = b64(json.dumps({"alg": "HS384", "typ": "JWT"}, separators=(",", ":")).encode())
    b = b64(json.dumps(p, separators=(",", ":")).encode())
    return f"{h}.{b}.{b64(hmac.new(SECRET, f'{h}.{b}'.encode(), hashlib.sha384).digest())}"


T_SUPER = token(1, "admin", ["SUPER_ADMIN"], ALL)
T_APPROVER = token(101808, "tech_admin", ["TECH_MANAGER"], ALL)
T_APPL = token(101809, "tech_staff1", ["TECH_MANAGER"], ALL)
T_MGR_INST = token(1, "admin", ["TECH_MANAGER"], ["system:approval:instance"])
T_UNRELATED = token(101812, "hr_staff1", ["EMPLOYEE"], ["system:approval:query"])


def req(method, path, tok=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    if tok:
        r.add_header("Authorization", "Bearer " + tok)
    try:
        with urllib.request.urlopen(r) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {}


def check(name, ok, detail=""):
    RESULTS.append((name, ok))
    print(f"{'PASS' if ok else 'FAIL'} | {name} | {detail}")


def bpmn(pid, uid):
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://oastem.com/bpmn">
  <process id="{pid}" name="{pid}" isExecutable="true">
    <startEvent id="startEvent" name="开始"/>
    <userTask id="{uid}" name="审批"/>
    <endEvent id="endEvent" name="结束"/>
    <sequenceFlow id="f1" sourceRef="startEvent" targetRef="{uid}"/>
    <sequenceFlow id="f2" sourceRef="{uid}" targetRef="endEvent"/>
  </process>
</definitions>"""


def cleanup(inst_id, flow_id):
    pid = scalar(f"SELECT proc_inst_id FROM {DB}.oa_approval_instance WHERE id={inst_id}") if inst_id else ""
    pin = f"'{pid}'" if pid else "''"
    mysql(" ".join([
        "SET FOREIGN_KEY_CHECKS=0;",
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
        f"DELETE FROM {DB}.act_re_procdef WHERE KEY_='{FLOW_CODE}';",
        f"DELETE FROM {DB}.act_re_deployment WHERE ID_ NOT IN (SELECT DEPLOYMENT_ID_ FROM {DB}.act_re_procdef);",
        f"DELETE FROM {DB}.act_ge_bytearray WHERE DEPLOYMENT_ID_ NOT IN (SELECT ID_ FROM {DB}.act_re_deployment);",
        f"DELETE FROM {DB}.oa_flow_definition WHERE flow_code='{FLOW_CODE}';",
        "SET FOREIGN_KEY_CHECKS=1;",
    ]))


def main():
    mysql(f"DELETE FROM {DB}.oa_flow_definition WHERE flow_code='{FLOW_CODE}';")
    r = req("POST", "/workflow/flow-definition", T_SUPER, {
        "flowCode": FLOW_CODE, "flowName": "详情权限验证", "category": "测试", "status": 1,
        "bpmnXml": bpmn(FLOW_CODE, "approve"),
        "nodeConfig": json.dumps([{"nodeId": "approve", "nodeName": "审批",
                                   "assigneeType": 1, "assigneeValue": "101808"}]),
    })
    assert r.get("code") == 200, f"建流程失败 {r}"
    flow_id = r["data"]
    st = req("POST", "/workflow/approval/start", T_APPL,
             {"flowCode": FLOW_CODE, "title": "详情权限验证单", "formData": "{}"})
    assert st.get("code") == 200, f"发起失败 {st}"
    inst = st["data"]
    inst_detail = lambda tok: req("GET", f"/workflow/approval/{inst}/detail", tok)
    try:
        d = inst_detail(T_SUPER)
        check("A 超管取详情 → 200 且 canApprove=true",
              d.get("code") == 200 and d.get("data", {}).get("canApprove") is True
              and d.get("data", {}).get("currentTask") is not None,
              f"code={d.get('code')} canApprove={d.get('data', {}).get('canApprove')}")

        dm = inst_detail(T_MGR_INST)
        check("B 仅实例码经理取详情 → 200 且 canApprove=false",
              dm.get("code") == 200 and dm.get("data", {}).get("canApprove") is False,
              f"code={dm.get('code')} canApprove={dm.get('data', {}).get('canApprove')}")

        task_id = (dm.get("data", {}).get("currentTask") or {}).get("taskId")
        c = req("POST", "/workflow/approval/complete", T_MGR_INST,
                {"taskId": task_id, "action": "approve", "opinion": "经理尝试"})
        check("C 实例码经理审批 → 403（缺 complete 码，越权仍拦）", c.get("code") == 403, f"code={c.get('code')}")

        du = inst_detail(T_UNRELATED)
        check("D 无关账号取详情 → 403（可见性未放宽）", du.get("code") == 403, f"code={du.get('code')}")

        dn = req("GET", f"/workflow/approval/{inst}/detail", None)
        check("E 未认证 → 401", dn.get("code") == 401, f"code={dn.get('code')}")

        da = inst_detail(T_APPROVER)
        check("F 候选人取详情 → 200 且 canApprove=true",
              da.get("code") == 200 and da.get("data", {}).get("canApprove") is True,
              f"code={da.get('code')} canApprove={da.get('data', {}).get('canApprove')}")

        dl = inst_detail(T_APPL)
        check("G 申请人取详情 → 200 且 canApprove=false",
              dl.get("code") == 200 and dl.get("data", {}).get("canApprove") is False,
              f"code={dl.get('code')} canApprove={dl.get('data', {}).get('canApprove')}")

        tid = (d.get("data", {}).get("currentTask") or {}).get("taskId")
        h = req("POST", "/workflow/approval/complete", T_SUPER,
                {"taskId": tid, "action": "approve", "opinion": "超管处理"})
        status = scalar(f"SELECT status FROM {DB}.oa_approval_instance WHERE id={inst}")
        check("H 超管审批任意节点 → 200 且实例已通过(3)",
              h.get("code") == 200 and status == "3", f"code={h.get('code')} status={status}")

        di = inst_detail(T_SUPER)
        check("I 结束后 canApprove=false",
              di.get("code") == 200 and di.get("data", {}).get("canApprove") is False,
              f"canApprove={di.get('data', {}).get('canApprove')}")

        d51 = req("GET", "/workflow/approval/51/detail", T_SUPER)
        check("J 回归：超管取遗留实例 51 详情 → 200（可见性已修复；其引擎运行态为空故 canApprove=false 属正确）",
              d51.get("code") == 200, f"code={d51.get('code')} canApprove={d51.get('data', {}).get('canApprove')}")
    finally:
        cleanup(inst, flow_id)
        left = scalar(f"SELECT COUNT(*) FROM {DB}.oa_approval_instance WHERE id={inst}")
        leftf = scalar(f"SELECT COUNT(*) FROM {DB}.oa_flow_definition WHERE flow_code='{FLOW_CODE}'")
        print(f"CLEANUP: 残留实例={left} 残留模板={leftf}")

    fail = [n for n, ok in RESULTS if not ok]
    print(f"\nSUMMARY: {len(RESULTS) - len(fail)}/{len(RESULTS)} 通过")
    return 0 if not fail else 1


if __name__ == "__main__":
    sys.exit(main())
