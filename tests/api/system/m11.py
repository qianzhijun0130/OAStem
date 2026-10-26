#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M11 个人中心 · 接口层验证（覆盖 TC-PROFILE-01 ~ 11）。
安全约定：改密用 tech_staff1，不用 admin；测试前备份资料与密码哈希，结束逐项还原。
"""
import base64, hashlib, hmac, json, os, re, subprocess, sys, time, urllib.error, urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:8080"
PROPS = [Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application.properties"),
         Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application-local.properties")]
MYSQL = r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe"
DB = "oa_system"
UID = 101809          # tech_staff1
OLD_PWD = "qwe@123456"
NEW_PWD = "Tmp@2026pass"
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


def login_pwd(username, pwd):
    return req("POST", "/auth/login", {"username": username, "password": pwd})


def ck(n, ok, d=""):
    R.append(ok)
    print(f"{'PASS' if ok else 'FAIL'} | {n} | {d}")


def main():
    T = token(UID, "tech_staff1", ["TECH_ENGINEER"])
    # ---------- 备份 ----------
    st, info = req("GET", "/profile/info", tok=T)
    p0 = info.get("data") or {}
    backup = {k: p0.get(k) for k in ("nickname", "gender", "mobile", "email", "avatar")}
    pwd_hash0 = mysql(f"SELECT password FROM {DB}.oa_user WHERE id={UID}")
    print("备份:", {k: backup[k] for k in backup}, "| hash 前12:", pwd_hash0[:12])

    # ---------- TC-01 查询 ----------
    ck("TC-01-N 返回完整资料", st == 200 and p0.get("id") == UID and p0.get("username") == "tech_staff1",
       f"http={st} id={p0.get('id')}")
    need = ["id", "username", "realName", "nickname", "avatar", "gender", "mobile", "email",
            "workNumber", "position", "entryDate", "deptId", "deptName", "roles"]
    missing = [k for k in need if k not in p0]
    ck("TC-01-N 字段齐全（含部门/角色）", not missing, f"missing={missing}")
    raw = json.dumps(info, ensure_ascii=False)
    ck("TC-01-N 不返回密码等敏感字段", "password" not in raw.lower(), f"len={len(raw)}")

    # ---------- TC-02 不接收 userId ----------
    st2, info2 = req("GET", "/profile/info?userId=101808", tok=T)
    ck("TC-02-E 传 userId 参数仍只返回自己（忽略入参）",
       (info2.get("data") or {}).get("id") == UID, f"id={(info2.get('data') or {}).get('id')}")

    st3, up = req("PUT", "/profile/update",
                  {"userId": 101808, "nickname": "越权昵称", "gender": 1, "mobile": "", "email": ""}, tok=T)
    owner = mysql(f"SELECT nickname FROM {DB}.oa_user WHERE id=101808")
    ck("TC-02-E body 传他人 userId 也只改自己", up.get("code") == 200 and owner != "越权昵称",
       f"code={up.get('code')} 101808.nickname={owner}")

    # ---------- TC-03 修改资料 ----------
    st4, up2 = req("PUT", "/profile/update",
                   {"nickname": "李伟改", "gender": 2, "mobile": "13900001111", "email": "lw@example.com"}, tok=T)
    st5, info3 = req("GET", "/profile/info", tok=T)
    d3 = info3.get("data") or {}
    ck("TC-03-N 四个自助字段均可修改",
       up2.get("code") == 200 and d3.get("nickname") == "李伟改" and d3.get("gender") == 2
       and d3.get("mobile") == "13900001111" and d3.get("email") == "lw@example.com",
       f"nickname={d3.get('nickname')} gender={d3.get('gender')} mobile={d3.get('mobile')}")

    # ---------- TC-04 校验 ----------
    cases = [
        ("昵称 51 字超长", {"nickname": "字" * 51, "gender": 1}, "昵称"),
        ("手机号格式非法", {"nickname": "ok", "mobile": "12345"}, "手机号"),
        ("邮箱格式非法", {"nickname": "ok", "email": "not-an-email"}, "邮箱"),
    ]
    for label, body, kw in cases:
        body.setdefault("gender", 1)
        stx, bx = req("PUT", "/profile/update", body, tok=T)
        ck(f"TC-04-E {label} → 400", bx.get("code") == 400, f"code={bx.get('code')} msg={str(bx.get('message'))[:40]}")

    # 边界：昵称正好 50 字应通过
    stx, bx = req("PUT", "/profile/update", {"nickname": "字" * 50, "gender": 1, "mobile": "", "email": ""}, tok=T)
    ck("TC-04-B 昵称 50 字（边界值）→ 200", bx.get("code") == 200, f"code={bx.get('code')}")

    # ---------- TC-05 头像 ----------
    stx, bx = req("PUT", "/profile/avatar", {"avatar": "/uploads/avatar/2026/10/test.png"}, tok=T)
    st6, info4 = req("GET", "/profile/info", tok=T)
    ck("TC-05-N /uploads/ 前缀头像可保存",
       bx.get("code") == 200 and (info4.get("data") or {}).get("avatar") == "/uploads/avatar/2026/10/test.png",
       f"avatar={(info4.get('data') or {}).get('avatar')}")

    # ---------- TC-06 头像白名单 ----------
    bad = ["/etc/passwd", "http://evil.com/a.png", "javascript:alert(1)", "/upload/x.png"]
    for b in bad:
        stx, bx = req("PUT", "/profile/avatar", {"avatar": b}, tok=T)
        ck(f"TC-06-E 非法头像 {b[:22]} → 拒绝", bx.get("code") == 400, f"code={bx.get('code')}")

    stx, bx = req("PUT", "/profile/avatar", {"avatar": ""}, tok=T)
    ck("TC-06-E 空头像 → 400（@NotBlank）", bx.get("code") == 400, f"code={bx.get('code')}")

    # ---------- TC-08 原密码错误 ----------
    stx, bx = req("PUT", "/profile/password", {"oldPassword": "wrong-pass", "newPassword": NEW_PWD}, tok=T)
    ck("TC-08-E 原密码错误 → 1007", bx.get("code") == 1007, f"code={bx.get('code')} msg={bx.get('message')}")
    hash_after_wrong = mysql(f"SELECT password FROM {DB}.oa_user WHERE id={UID}")
    ck("TC-08-E 原密码错误时密码未被改动", hash_after_wrong == pwd_hash0, "")

    # ---------- TC-09 新密码规则 ----------
    for label, pwd in [("5 位过短", "Aa1x5"), ("21 位过长", "A" * 21)]:
        stx, bx = req("PUT", "/profile/password", {"oldPassword": OLD_PWD, "newPassword": pwd}, tok=T)
        ck(f"TC-09-E 新密码{label} → 400", bx.get("code") == 400, f"code={bx.get('code')}")
    stx, bx = req("PUT", "/profile/password", {"oldPassword": OLD_PWD, "newPassword": OLD_PWD}, tok=T)
    ck("TC-09-E 新旧密码相同 → 400", bx.get("code") == 400, f"code={bx.get('code')} msg={str(bx.get('message'))[:30]}")

    # ---------- TC-07 改密正常 ----------
    stx, bx = req("PUT", "/profile/password", {"oldPassword": OLD_PWD, "newPassword": NEW_PWD}, tok=T)
    st_old, b_old = login_pwd("tech_staff1", OLD_PWD)
    st_new, b_new = login_pwd("tech_staff1", NEW_PWD)
    ck("TC-07-N 改密成功且新密码可登录、旧密码失效",
       bx.get("code") == 200 and b_new.get("code") == 200 and b_old.get("code") == 1001,
       f"change={bx.get('code')} new={b_new.get('code')} old={b_old.get('code')}")

    # ---------- TC-10 改密日志脱敏 ----------
    log = mysql(f"SELECT COUNT(*) FROM {DB}.oa_operation_log WHERE module='个人中心' AND operation='修改登录密码' "
                f"AND request_params IS NOT NULL AND (request_params LIKE '%{NEW_PWD}%' OR request_params LIKE '%{OLD_PWD}%')")
    total = mysql(f"SELECT COUNT(*) FROM {DB}.oa_operation_log WHERE module='个人中心' AND operation='修改登录密码' "
                  f"AND create_time >= NOW() - INTERVAL 10 MINUTE")
    ck("TC-10-S 改密日志不落密码明文（saveParams=false）", log == "0" and total != "0",
       f"日志条数={total} 含明文条数={log}")

    # ---------- TC-11 未登录 ----------
    for path in ["/profile/info"]:
        stx, _ = req("GET", path)
        ck(f"TC-11-E 未登录 {path} → 401", stx == 401, f"http={stx}")
    stx, _ = req("PUT", "/profile/update", {"nickname": "x", "gender": 1})
    ck("TC-11-E 未登录修改资料 → 401", stx == 401, f"http={stx}")

    # ---------- 还原 ----------
    # 用新密码把密码改回
    T2 = token(UID, "tech_staff1", ["TECH_ENGINEER"])
    rsp, rb = req("PUT", "/profile/password", {"oldPassword": NEW_PWD, "newPassword": OLD_PWD}, tok=T2)
    relogin = login_pwd("tech_staff1", OLD_PWD)[1].get("code")
    ck("回滚 密码已还原（原密码可登录）", rb.get("code") == 200 and relogin == 200,
       f"change_back={rb.get('code')} relogin={relogin}")

    restore = {"nickname": backup["nickname"] or "", "gender": backup["gender"],
               "mobile": backup["mobile"] or "", "email": backup["email"] or ""}
    req("PUT", "/profile/update", restore, tok=T2)
    # 空串 != NULL：原值为 NULL 的列需直接回写，否则会留下 '' 的差异
    if backup["nickname"] is None:
        mysql(f"UPDATE {DB}.oa_user SET nickname=NULL WHERE id={UID}")
    if backup["avatar"]:
        req("PUT", "/profile/avatar", {"avatar": backup["avatar"]}, tok=T2)
    _, info5 = req("GET", "/profile/info", tok=T2)
    d5 = info5.get("data") or {}
    nick_db = mysql(f"SELECT IFNULL(nickname,'<NULL>') FROM {DB}.oa_user WHERE id={UID}")
    nick_ok = (backup["nickname"] is None and nick_db == "<NULL>") or (d5.get("nickname") == backup["nickname"])
    same = nick_ok and all((d5.get(k) or "") == (backup[k] or "") for k in ("mobile", "email")) \
        and d5.get("gender") == backup["gender"]
    ck("回滚 资料已还原", same, f"now={ {k: d5.get(k) for k in ('nickname','gender','mobile','email')} }")

    fail = [x for x in R if not x]
    print(f"\nSUMMARY: {len(R) - len(fail)}/{len(R)} 通过")
    return 0 if not fail else 1


sys.exit(main())
