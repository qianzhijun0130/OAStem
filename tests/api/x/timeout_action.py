#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""阶段四·到期自动动作验证。

A 动作=1 提醒：到期后状态仍为 2、写 1 条 action=timeout 记录、timeout_handled_at 有值
B 幂等：再过一轮不产生第 2 条记录
C 动作=2 自动驳回：到期后状态变 4、引擎运行态清空
"""
import base64, datetime, hashlib, hmac, json, os, subprocess, sys, time, urllib.error, urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:8080"
PROPS = [Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application.properties"),
         Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application-local.properties")]
MYSQL = r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe"
DB = "oa_system"
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
U = PD.get("spring.datasource.username", "root")
U = U.split(":")[-1].rstrip("}") if U.startswith("${") else U
PW = PD.get("spring.datasource.password", "")
SECRET_PW = PW


def mysql(sql):
    return subprocess.run([MYSQL, "--force", f"-u{U}", "-N", "-e", sql],
                          capture_output=True, text=True, env=dict(os.environ, MYSQL_PWD=SECRET_PW)).stdout.strip()


def scalar(sql):
    o = mysql(sql)
    return o.splitlines()[0] if o else ""


def b64(d): return base64.urlsafe_b64encode(d).rstrip(b"=").decode()


def tok(perms):
    now = int(time.time())
    p = {"sub": "admin", "userId": 1, "roles": ["SUPER_ADMIN"], "perms": perms, "iat": now, "exp": now + 3600}
    h = b64(json.dumps({"alg": "HS384", "typ": "JWT"}, separators=(",", ":")).encode())
    b = b64(json.dumps(p, separators=(",", ":")).encode())
    return f"{h}.{b}.{b64(hmac.new(SEC, f'{h}.{b}'.encode(), hashlib.sha384).digest())}"


PERMS = [x for x in mysql(f"SELECT menu_code FROM {DB}.oa_menu WHERE menu_code IS NOT NULL AND menu_code<>''").splitlines() if x]
T = tok(PERMS)


def post(path, body):
    r = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="POST",
                               headers={"Content-Type": "application/json", "Authorization": "Bearer " + T})
    try:
        with urllib.request.urlopen(r) as x: return json.loads(x.read().decode())
    except urllib.error.HTTPError as e: return json.loads(e.read().decode())


def check(n, ok, d=""):
    RESULTS.append((n, ok)); print(f"{'PASS' if ok else 'FAIL'} | {n} | {d}")


def bpmn(pid):
    return f'''<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://oastem.com/bpmn"><process id="{pid}" isExecutable="true"><startEvent id="s"/><userTask id="approve" name="审批"/><endEvent id="e"/><sequenceFlow id="f1" sourceRef="s" targetRef="approve"/><sequenceFlow id="f2" sourceRef="approve" targetRef="e"/></process></definitions>'''


def make_flow(code, action):
    mysql(f"DELETE FROM {DB}.oa_flow_definition WHERE flow_code='{code}';")
    r = post("/workflow/flow-definition", {
        "flowCode": code, "flowName": f"到期动作-{code}", "category": "测试", "status": 1,
        "bpmnXml": bpmn(code),
        "nodeConfig": json.dumps([{"nodeId": "approve", "nodeName": "审批", "assigneeType": 1,
                                   "assigneeValue": "101808", "timeoutHours": 1, "timeoutAction": action}])})
    assert r.get("code") == 200, r


def start(code, title):
    s = post("/workflow/approval/start", {"flowCode": code, "title": title, "formData": "{}"})
    assert s.get("code") == 200, s
    return s["data"]


def force_due(inst):
    mysql(f"UPDATE {DB}.oa_approval_instance SET due_time = DATE_SUB(NOW(), INTERVAL 1 MINUTE), "
          f"timeout_handled_at = NULL WHERE id={inst};")


def wait_for(fn, timeout=90, step=3):
    end = time.time() + timeout
    while time.time() < end:
        if fn():
            return True
        time.sleep(step)
    return False


def records(inst):
    return int(scalar(f"SELECT COUNT(*) FROM {DB}.oa_approval_record WHERE instance_id={inst} AND action='timeout'") or 0)


def cleanup(inst):
    pid = scalar(f"SELECT proc_inst_id FROM {DB}.oa_approval_instance WHERE id={inst}") if inst else ""
    pin = f"'{pid}'" if pid else "''"
    mysql(" ".join(["SET FOREIGN_KEY_CHECKS=0;",
                    f"DELETE FROM {DB}.oa_approval_record WHERE instance_id={inst};" if inst else "SELECT 1;",
                    f"DELETE FROM {DB}.oa_approval_instance WHERE id={inst};" if inst else "SELECT 1;",
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
                    f"DELETE FROM {DB}.act_re_procdef WHERE KEY_ LIKE 't_toa_%';",
                    f"DELETE FROM {DB}.act_re_deployment WHERE ID_ NOT IN (SELECT DEPLOYMENT_ID_ FROM {DB}.act_re_procdef);",
                    f"DELETE FROM {DB}.act_ge_bytearray WHERE DEPLOYMENT_ID_ NOT IN (SELECT ID_ FROM {DB}.act_re_deployment);",
                    f"DELETE FROM {DB}.oa_flow_definition WHERE flow_code LIKE 't_toa_%';",
                    "SET FOREIGN_KEY_CHECKS=1;"]))


def main():
    mysql(f"DELETE FROM {DB}.oa_flow_definition WHERE flow_code LIKE 't_toa_%';")
    make_flow("t_toa_remind", 1)
    make_flow("t_toa_reject", 2)
    inst_r = start("t_toa_remind", "到期提醒验证")
    inst_j = start("t_toa_reject", "到期驳回验证")
    try:
        force_due(inst_r)
        force_due(inst_j)
        ok = wait_for(lambda: records(inst_r) >= 1 and records(inst_j) >= 1, timeout=100)
        check("R1 到期后写入超时记录（提醒）", ok and records(inst_r) == 1, f"records={records(inst_r)}")
        st_r = scalar(f"SELECT status FROM {DB}.oa_approval_instance WHERE id={inst_r}")
        handled = scalar(f"SELECT IFNULL(timeout_handled_at,'') FROM {DB}.oa_approval_instance WHERE id={inst_r}")
        check("R2 提醒不改状态，且标记已处置",
              st_r == "2" and handled != "", f"status={st_r} handled={handled}")

        st_j = scalar(f"SELECT status FROM {DB}.oa_approval_instance WHERE id={inst_j}")
        ru = scalar(f"SELECT COUNT(*) FROM {DB}.act_ru_task WHERE PROC_INST_ID_=(SELECT proc_inst_id FROM {DB}.oa_approval_instance WHERE id={inst_j})")
        check("J1 自动驳回 → 状态 4 且引擎运行态清空",
              st_j == "4" and ru == "0", f"status={st_j} ru_task={ru}")

        # 幂等：等下一轮，记录数不应增加
        print("... 等待下一轮调度验证幂等（约 70s）")
        time.sleep(70)
        check("R3 幂等：下一轮不重复写记录", records(inst_r) == 1, f"records={records(inst_r)}")
    finally:
        cleanup(inst_r)
        cleanup(inst_j)
        print("CLEANUP 残留=", scalar(f"SELECT COUNT(*) FROM {DB}.oa_approval_instance WHERE id IN ({inst_r},{inst_j})"),
              "模板=", scalar(f"SELECT COUNT(*) FROM {DB}.oa_flow_definition WHERE flow_code LIKE 't_toa_%'"))

    fail = [n for n, ok in RESULTS if not ok]
    print(f"\nSUMMARY: {len(RESULTS) - len(fail)}/{len(RESULTS)} 通过")
    return 0 if not fail else 1


if __name__ == "__main__":
    sys.exit(main())
