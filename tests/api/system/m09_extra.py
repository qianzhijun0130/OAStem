#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M09 补充：TC-APPROVAL-29（抄送）与 TC-APPROVAL-30（草稿）的异常 / 边界分支。
覆盖：未认证、实例不存在、无效用户、幂等、越权提交他人草稿、非草稿提交、草稿不产待办、抄送不产待办。
"""
import base64, hashlib, hmac, json, os, re, subprocess, sys, time, urllib.error, urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:8080"
PROPS = [Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application.properties"),
         Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application-local.properties")]
MYSQL = r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe"
DB = "oa_system"
R = []


def load():
    d = {}
    for p in PROPS:
        if p.exists():
            for line in p.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    d[k.strip()] = v.strip()
    return d


def resolve(v, fb=""):
    if not v:
        return fb
    m = re.fullmatch(r"\$\{([A-Za-z0-9_]+)(?::([^}]*))?\}", v.strip())
    if m:
        return os.environ.get(m.group(1)) or (m.group(2) if m.group(2) is not None else fb)
    return v


PD = load()
SEC = PD["jwt.secret"].encode()
DB_USER = resolve(PD.get("spring.datasource.username"), "root")
DB_PASS = resolve(PD.get("spring.datasource.password"), "")


def mysql(sql):
    return subprocess.run([MYSQL, "--force", f"-u{DB_USER}", "-N", "-e", sql],
                          capture_output=True, text=True,
                          env=dict(os.environ, MYSQL_PWD=DB_PASS)).stdout.strip()


def b64(x):
    return base64.urlsafe_b64encode(x).rstrip(b"=").decode()


def token(uid, name, roles, hours=4):
    now = int(time.time())
    perms = [x for x in mysql(
        f"SELECT menu_code FROM {DB}.oa_menu WHERE menu_code IS NOT NULL AND menu_code<>''").splitlines() if x]
    payload = {"sub": name, "userId": uid, "roles": roles, "perms": perms, "iat": now, "exp": now + hours * 3600}
    h = b64(json.dumps({"alg": "HS384", "typ": "JWT"}, separators=(",", ":")).encode())
    b = b64(json.dumps(payload, separators=(",", ":")).encode())
    return f"{h}.{b}.{b64(hmac.new(SEC, f'{h}.{b}'.encode(), hashlib.sha384).digest())}"


def req(method, path, body=None, tok=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if tok:
        h["Authorization"] = "Bearer " + tok
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r) as x:
            return x.status, json.loads(x.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def ck(n, ok, d=""):
    R.append(ok)
    print(f"{'PASS' if ok else 'FAIL'} | {n} | {d}")


def cleanup(ids):
    for i in ids:
        pid = mysql(f"SELECT IFNULL(proc_inst_id,'') FROM {DB}.oa_approval_instance WHERE id={i}") or ""
        pin = f"'{pid}'" if pid else "''"
        mysql(" ".join(["SET FOREIGN_KEY_CHECKS=0;",
                        f"DELETE FROM {DB}.oa_approval_record WHERE instance_id={i};",
                        f"DELETE FROM {DB}.oa_approval_cc WHERE instance_id={i};",
                        f"DELETE FROM {DB}.oa_approval_instance WHERE id={i};",
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
                        "SET FOREIGN_KEY_CHECKS=1;"]))


def main():
    T_ADMIN = token(1, "admin", ["SUPER_ADMIN"])
    T_STAFF = token(101809, "tech_staff1", ["TECH_ENGINEER"])
    made = []

    # ---------- TC-29 抄送 ----------
    st, _ = req("POST", "/workflow/approval/cc", {"instanceId": 1, "userIds": [1]}, tok=None)
    ck("TC29-E1 未认证调抄送接口 → 401", st == 401, f"http={st}")

    st, b = req("POST", "/workflow/approval/cc", {"instanceId": 999999, "userIds": [1]}, tok=T_ADMIN)
    ck("TC29-E2 抄送不存在的审批单 → 3101", b.get("code") == 3101, f"code={b.get('code')} msg={b.get('message')}")

    # 造一个真实实例（起草稿即可，抄送不要求实例在审批中）
    _, d = req("POST", "/workflow/approval/draft",
               {"flowCode": "expense", "title": "抄送补充验证单", "formData": "{}"}, tok=T_ADMIN)
    iid = d.get("data")
    made.append(iid)

    st, b = req("POST", "/workflow/approval/cc", {"instanceId": iid, "userIds": [999999]}, tok=T_ADMIN)
    rows = mysql(f"SELECT COUNT(*) FROM {DB}.oa_approval_cc WHERE instance_id={iid}")
    ck("TC29-E3 抄送给不存在的用户 → 200 且不落脏数据", b.get("code") == 200 and rows == "0",
       f"code={b.get('code')} rows={rows}")

    # 抄送不产待办：抄送前后 tech_staff1 待办数不变
    _, before = req("GET", "/workflow/approval/todo?pageNo=1&pageSize=50", tok=T_STAFF)
    n_before = before.get("data", {}).get("total")
    req("POST", "/workflow/approval/cc", {"instanceId": iid, "userIds": [101809], "remark": "抄送不产待办"}, tok=T_ADMIN)
    _, after = req("GET", "/workflow/approval/todo?pageNo=1&pageSize=50", tok=T_STAFF)
    n_after = after.get("data", {}).get("total")
    _, page = req("GET", f"/workflow/approval/cc/page?readFlag=0&pageNo=1&pageSize=10", tok=T_STAFF)
    seen = [x.get("instanceId") for x in (page.get("data", {}).get("records") or [])]
    ck("TC29-N2 抄送不产生待办，但出现在抄送列表", n_before == n_after and iid in seen,
       f"todo {n_before}→{n_after} cc列表含={iid in seen}")

    cc_id = mysql(f"SELECT id FROM {DB}.oa_approval_cc WHERE instance_id={iid} AND user_id=101809 LIMIT 1")
    _, b = req("POST", f"/workflow/approval/cc/read?ccId={cc_id}", tok=T_ADMIN)
    flag_by_other = mysql(f"SELECT read_flag FROM {DB}.oa_approval_cc WHERE id={cc_id}")
    ck("TC29-E4 越权标记他人抄送已读 → 3101 且状态不变",
       b.get("code") == 3101 and flag_by_other == "0", f"code={b.get('code')} flag={flag_by_other}")

    _, b = req("POST", f"/workflow/approval/cc/read?ccId={cc_id}", tok=T_STAFF)
    flag = mysql(f"SELECT read_flag FROM {DB}.oa_approval_cc WHERE id={cc_id}")
    ck("TC29-N3 被抄送人标记已读 → read_flag=1", b.get("code") == 200 and flag == "1", f"code={b.get('code')} flag={flag}")

    # 全部已读：把该用户的抄送重置为未读，再 read-all
    mysql(f"UPDATE {DB}.oa_approval_cc SET read_flag=0, read_time=NULL WHERE user_id=101809")
    before_unread = mysql(f"SELECT COUNT(*) FROM {DB}.oa_approval_cc WHERE user_id=101809 AND read_flag=0")
    _, b = req("POST", "/workflow/approval/cc/read-all", tok=T_STAFF)
    left_unread = mysql(f"SELECT COUNT(*) FROM {DB}.oa_approval_cc WHERE user_id=101809 AND read_flag=0")
    ck("TC29-N5 全部已读 → 本人未读清零", b.get("code") == 200 and before_unread != "0" and left_unread == "0",
       f"before_unread={before_unread} after_unread={left_unread}")

    rec = mysql(f"SELECT COUNT(*) FROM {DB}.oa_approval_record WHERE instance_id={iid} AND action='cc'")
    ck("TC29-N4 抄送写入审批记录（action=cc）", rec == "1", f"record={rec}")

    # ---------- TC-30 草稿 ----------
    _, d = req("POST", "/workflow/approval/draft",
               {"flowCode": "expense", "title": "越权验证草稿", "formData": "{}"}, tok=T_ADMIN)
    did = d.get("data")
    made.append(did)

    st, b = req("POST", f"/workflow/approval/{did}/submit", tok=T_STAFF)
    ck("TC30-E1 他人提交我的草稿 → 3101（不暴露存在性）", b.get("code") == 3101, f"code={b.get('code')}")

    st, b = req("PUT", f"/workflow/approval/draft/{did}",
                {"flowCode": "expense", "title": "他人改标题", "formData": "{}"}, tok=T_STAFF)
    ck("TC30-E2 他人更新我的草稿 → 3101", b.get("code") == 3101, f"code={b.get('code')}")

    # 草稿不产待办
    _, todo = req("GET", "/workflow/approval/todo?pageNo=1&pageSize=50", tok=T_ADMIN)
    ids = [x.get("instanceId") for x in (todo.get("data", {}).get("records") or [])]
    ck("TC30-N3 草稿不进入任何人待办", did not in ids, f"待办含草稿={did in ids}")

    # 正常提交后再提交 → 3102
    _, b = req("POST", f"/workflow/approval/{did}/submit", tok=T_ADMIN)
    ok_submit = b.get("code") == 200
    _, b2 = req("POST", f"/workflow/approval/{did}/submit", tok=T_ADMIN)
    ck("TC30-E3 重复提交已提交的草稿 → 3102",
       ok_submit and b2.get("code") == 3102, f"first={b.get('code')} second={b2.get('code')} msg={b2.get('message')}")

    _, b3 = req("PUT", f"/workflow/approval/draft/{did}",
                {"flowCode": "expense", "title": "已提交后再改", "formData": "{}"}, tok=T_ADMIN)
    ck("TC30-E4 已提交的单据不能再按草稿更新 → 3102", b3.get("code") == 3102, f"code={b3.get('code')}")

    cleanup(made)
    left = mysql(f"SELECT COUNT(*) FROM {DB}.oa_approval_instance WHERE id IN ({','.join(map(str, made))})")
    ccs = mysql(f"SELECT COUNT(*) FROM {DB}.oa_approval_cc")
    print(f"CLEANUP 残留实例={left} 抄送总行={ccs}")

    fail = [x for x in R if not x]
    print(f"\nSUMMARY: {len(R) - len(fail)}/{len(R)} 通过")
    return 0 if not fail else 1


sys.exit(main())
