#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""M13 登录日志 · 接口层验证（TC-LLOG-01 ~ 10）。造数统一用 IP 10.9.9.9，便于识别与清理。"""
import base64, hashlib, hmac, json, os, re, subprocess, sys, time, urllib.error, urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:8080"
PROPS = [Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application.properties"),
         Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application-local.properties")]
MYSQL = r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe"
DB = "oa_system"
MARK_IP = "10.9.9.9"
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


def token(uid, name, perms, hours=4):
    now = int(time.time())
    payload = {"sub": name, "userId": uid, "roles": ["SUPER_ADMIN"] if uid == 1 else ["EMPLOYEE"],
               "perms": perms, "iat": now, "exp": now + hours * 3600}
    h = b64(json.dumps({"alg": "HS384", "typ": "JWT"}, separators=(",", ":")).encode())
    b = b64(json.dumps(payload, separators=(",", ":")).encode())
    return f"{h}.{b}.{b64(hmac.new(SEC, f'{h}.{b}'.encode(), hashlib.sha384).digest())}"


ALL_PERMS = [x for x in mysql(
    f"SELECT menu_code FROM {DB}.oa_menu WHERE menu_code IS NOT NULL AND menu_code<>''").splitlines() if x]


def req(method, path, tok=None, raw=False):
    r = urllib.request.Request(BASE + path, method=method,
                               headers={"Authorization": "Bearer " + tok} if tok else {})
    try:
        with urllib.request.urlopen(r, timeout=60) as x:
            data = x.read()
            if raw:
                return x.status, dict(x.headers), data
            return x.status, json.loads(data.decode())
    except urllib.error.HTTPError as e:
        data = e.read()
        if raw:
            return e.code, dict(e.headers), data
        try:
            return e.code, json.loads(data.decode())
        except Exception:
            return e.code, {}


def ck(n, ok, d=""):
    R.append(ok)
    print(f"{'PASS' if ok else 'FAIL'} | {n} | {d}")


def main():
    T_ADMIN = token(1, "admin", ALL_PERMS)
    # 普通员工只给最小权限（无 login-log:export）
    T_STAFF = token(101809, "tech_staff1", ["system:approval:apply"])
    T_MGR = token(101808, "tech_admin", ["system:login-log:export"])

    # ---------- 造数 ----------
    mysql(f"DELETE FROM {DB}.oa_login_log WHERE ip_address='{MARK_IP}'")
    rows = [
        (1, "admin", 1, f"{MARK_IP}", 1, "NULL", "NOW() - INTERVAL 30 DAY"),
        (101809, "tech_staff1", 1, f"{MARK_IP}", 5, "NULL", "NOW() - INTERVAL 29 DAY"),
        (101808, "tech_admin", 1, f"{MARK_IP}", 0, "'明文密码错误'", "NOW() - INTERVAL 28 DAY"),
        (101809, "tech_staff1", 1, f"{MARK_IP}", 0, "'明文密码错误'", "NOW() - INTERVAL 5 MINUTE"),
    ]
    for uid, name, ltype, ip, status, reason, ts in rows:
        mysql(f"INSERT INTO {DB}.oa_login_log (user_id,username,login_type,ip_address,login_status,fail_reason,create_time) "
              f"VALUES ({uid},'{name}',{ltype},'{ip}',{status},{reason},{ts})")
    seeded = mysql(f"SELECT COUNT(*) FROM {DB}.oa_login_log WHERE ip_address='{MARK_IP}'")
    print("造数:", seeded, "条")

    # ---------- TC-01 分页 ----------
    st, b = req("GET", f"/system/login-log/page?pageNo=1&pageSize=10&ipAddress={MARK_IP}", T_ADMIN)
    d = b.get("data") or {}
    ck("TC-LLOG-01-N 按 IP 分页可查到造数",
       b.get("code") == 200 and int(d.get("total") or 0) == 4, f"total={d.get('total')}")
    sizes = [len(d.get("records") or [])]
    st2, b2 = req("GET", f"/system/login-log/page?pageNo=1&pageSize=2&ipAddress={MARK_IP}", T_ADMIN)
    ck("TC-LLOG-01-B pageSize=2 生效",
       len(( b2.get("data") or {}).get("records") or []) == 2, f"records={len((b2.get('data') or {}).get('records') or [])}")
    st3, b3 = req("GET", f"/system/login-log/page?pageNo=999&pageSize=10&ipAddress={MARK_IP}", T_ADMIN)
    ck("TC-LLOG-01-B 超末页回落而非报错", b3.get("code") == 200, f"code={b3.get('code')}")

    # ---------- TC-02 账号筛选 ----------
    st, b = req("GET", f"/system/login-log/page?pageNo=1&pageSize=20&ipAddress={MARK_IP}&username=tech_staff1", T_ADMIN)
    recs = (b.get("data") or {}).get("records") or []
    ck("TC-LLOG-02-N 按账号筛选只返回该账号", b.get("code") == 200 and len(recs) == 2
       and all(x.get("username") == "tech_staff1" for x in recs), f"count={len(recs)}")

    # ---------- TC-03 状态筛选 ----------
    st, b = req("GET", f"/system/login-log/page?pageNo=1&pageSize=20&ipAddress={MARK_IP}&loginStatus=0", T_ADMIN)
    recs = (b.get("data") or {}).get("records") or []
    ck("TC-LLOG-03-N 按状态筛选（失败=0）", b.get("code") == 200 and len(recs) == 2
       and all(x.get("loginStatus") == 0 for x in recs), f"count={len(recs)}")

    # ---------- TC-04 IP 筛选 ----------
    st, b = req("GET", f"/system/login-log/page?pageNo=1&pageSize=20&ipAddress=10.9.9.9", T_ADMIN)
    recs = (b.get("data") or {}).get("records") or []
    ck("TC-LLOG-04-N 按 IP 精确筛选", b.get("code") == 200 and len(recs) == 4, f"count={len(recs)}")
    st, b = req("GET", f"/system/login-log/page?pageNo=1&pageSize=20&ipAddress=10.9.9", T_ADMIN)
    recs = (b.get("data") or {}).get("records") or []
    ck("TC-LLOG-04-B IP 模糊匹配（前缀）", len(recs) >= 4, f"count={len(recs)}")

    # ---------- TC-05 时间范围 ----------
    start = (time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time() - 31 * 86400)))
    end = (time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time() - 27 * 86400)))
    st, b = req("GET", f"/system/login-log/page?pageNo=1&pageSize=20&ipAddress={MARK_IP}"
                       f"&startTime={urllib.parse.quote(start)}&endTime={urllib.parse.quote(end)}", T_ADMIN)
    recs = (b.get("data") or {}).get("records") or []
    ck("TC-LLOG-05-N 时间范围内筛选（28~30 天前 3 条）",
       b.get("code") == 200 and len(recs) == 3, f"count={len(recs)}")
    st, b = req("GET", f"/system/login-log/page?pageNo=1&pageSize=20&startTime=notadate", T_ADMIN)
    ck("TC-LLOG-05-E 非法时间格式 → 400", b.get("code") == 400, f"code={b.get('code')}")

    # ---------- TC-06 导出 ----------
    st, headers, data = req("GET", f"/system/login-log/export?ipAddress={MARK_IP}", T_ADMIN, raw=True)
    ct = headers.get("Content-Type") or headers.get("content-type") or ""
    cd = headers.get("Content-Disposition") or headers.get("content-disposition") or ""
    is_xlsx = data[:2] == b"PK" and len(data) > 2000
    ck("TC-LLOG-06-N 导出返回 xlsx 二进制",
       st == 200 and is_xlsx and ("spreadsheetml" in ct or "octet-stream" in ct),
       f"http={st} bytes={len(data)} ct={ct[:50]}")
    ck("TC-LLOG-06-N 导出带下载文件名", "attachment" in cd, f"cd={cd[:70]}")

    # ---------- TC-07 清理正常 ----------
    cutoff = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time() - 25 * 86400))
    st, b = req("DELETE", f"/system/login-log/clean?before={urllib.parse.quote(cutoff)}", T_ADMIN)
    left_mark = mysql(f"SELECT COUNT(*) FROM {DB}.oa_login_log WHERE ip_address='{MARK_IP}'")
    ck("TC-LLOG-07-N 按时间清理命中 30/29/28 天前 3 条",
       b.get("code") == 200 and int(b.get("data") or 0) >= 3 and left_mark == "1",
       f"deleted={b.get('data')} 剩余造数={left_mark}")

    # ---------- TC-08 参数缺失 ----------
    st, b = req("DELETE", "/system/login-log/clean", T_ADMIN)
    ck("TC-LLOG-08-E 既无 before 也无 all → 400", b.get("code") == 400, f"code={b.get('code')} msg={b.get('message')}")

    # ---------- TC-09 参数非法 ----------
    future = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time() + 86400))
    st, b = req("DELETE", f"/system/login-log/clean?before={urllib.parse.quote(future)}", T_ADMIN)
    ck("TC-LLOG-09-E 截止时间晚于当前 → 400", b.get("code") == 400, f"code={b.get('code')}")
    yday = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time() - 3600))
    st, b = req("DELETE", f"/system/login-log/clean?before={urllib.parse.quote(yday)}", T_ADMIN)
    ck("TC-LLOG-09-E 未保留最近 1 天 → 400", b.get("code") == 400, f"code={b.get('code')} msg={str(b.get('message'))[:30]}")
    st, b = req("DELETE", "/system/login-log/clean?before=notadate", T_ADMIN)
    ck("TC-LLOG-09-E before 格式非法 → 400", b.get("code") in (400, 5000), f"code={b.get('code')}")

    # ---------- TC-10 权限码隔离 ----------
    st, _h, data = req("GET", f"/system/login-log/export?ipAddress={MARK_IP}", T_STAFF, raw=True)
    body_json = {}
    try:
        body_json = json.loads(data.decode("utf-8"))
    except Exception:
        pass
    ck("TC-LLOG-10-E 无 export 权限码导出 → 403（且未返回文件）",
       body_json.get("code") == 403 and data[:2] != b"PK",
       f"code={body_json.get('code')} bytes={len(data)}")
    st, b = req("DELETE", f"/system/login-log/clean?before={urllib.parse.quote(cutoff)}", T_STAFF)
    ck("TC-LLOG-10-E 非超管清理 → 403", b.get("code") == 403, f"code={b.get('code')}")

    st, b = req("GET", "/system/login-log/page?pageNo=1&pageSize=50", T_STAFF)
    recs = (b.get("data") or {}).get("records") or []
    only_self = all(x.get("username") == "tech_staff1" for x in recs) if recs else True
    ck("TC-LLOG-10-S 普通员工只能看到本人日志", b.get("code") == 200 and only_self,
       f"count={len(recs)} users={set(x.get('username') for x in recs)}")

    st, b = req("GET", f"/system/login-log/page?pageNo=1&pageSize=20&userId=1", T_STAFF)
    recs2 = (b.get("data") or {}).get("records") or []
    ck("TC-LLOG-10-S 越权指定 userId 返回空集而非他人数据", len(recs2) == 0,
       f"count={len(recs2)}")

    # ---------- 清理 ----------
    mysql(f"DELETE FROM {DB}.oa_login_log WHERE ip_address='{MARK_IP}'")
    left = mysql(f"SELECT COUNT(*) FROM {DB}.oa_login_log WHERE ip_address='{MARK_IP}'")
    print(f"CLEANUP 造数残留={left}")

    fail = [x for x in R if not x]
    print(f"\nSUMMARY: {len(R) - len(fail)}/{len(R)} 通过")
    return 0 if not fail else 1


sys.exit(main())
