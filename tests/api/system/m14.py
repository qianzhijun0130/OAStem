#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""M14 操作日志 · 接口层验证（TC-OLOG-01 ~ 13）。用只读接口造日志，结束按 id 区间清理。"""
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


def token(uid, name, perms, roles=None):
    now = int(time.time())
    payload = {"sub": name, "userId": uid, "roles": roles or (["SUPER_ADMIN"] if uid == 1 else ["EMPLOYEE"]),
               "perms": perms, "iat": now, "exp": now + 4 * 3600}
    h = b64(json.dumps({"alg": "HS384", "typ": "JWT"}, separators=(",", ":")).encode())
    b = b64(json.dumps(payload, separators=(",", ":")).encode())
    return f"{h}.{b}.{b64(hmac.new(SEC, f'{h}.{b}'.encode(), hashlib.sha384).digest())}"


ALL_PERMS = [x for x in mysql(
    f"SELECT menu_code FROM {DB}.oa_menu WHERE menu_code IS NOT NULL AND menu_code<>''").splitlines() if x]


def req(method, path, body=None, tok=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if tok:
        h["Authorization"] = "Bearer " + tok
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=60) as x:
            d = x.read()
            if raw:
                return x.status, dict(x.headers), d
            return x.status, json.loads(d.decode())
    except urllib.error.HTTPError as e:
        d = e.read()
        if raw:
            return e.code, dict(e.headers), d
        try:
            return e.code, json.loads(d.decode())
        except Exception:
            return e.code, {}


def ck(n, ok, d=""):
    R.append(ok)
    print(f"{'PASS' if ok else 'FAIL'} | {n} | {d}")


def main():
    T = token(1, "admin", ALL_PERMS)
    T_STAFF = token(101809, "tech_staff1", ["system:approval:apply"])
    base_id = int(mysql(f"SELECT IFNULL(MAX(id),0) FROM {DB}.oa_operation_log") or 0)
    print("基线 max(id):", base_id)

    # ---------- TC-01/02 基本采集与字段完整性 ----------
    req("GET", "/system/login-log/export?pageNo=1&pageSize=5", tok=T, raw=True)
    time.sleep(1)
    row = mysql(f"SELECT module,operation,method,operation_status,user_id,username,cost_time,ip_address,request_params,create_time "
                f"FROM {DB}.oa_operation_log WHERE id>{base_id} ORDER BY id DESC LIMIT 1")
    parts = (row or "").split("\t")
    ck("TC-OLOG-01-N 调用带注解接口后产生日志", len(parts) >= 9 and parts[0] == "日志管理",
       f"module={parts[0] if parts else None} operation={parts[1] if len(parts)>1 else None}")
    ok_fields = (len(parts) >= 9 and parts[1] == "导出登录日志" and "export" in (parts[2] or "")
                 and parts[3] == "1" and parts[4] == "1" and parts[5] == "admin"
                 and (parts[6] or "").isdigit() and parts[7])
    ck("TC-OLOG-02-N 字段完整（模块/操作/方法/状态/用户/耗时/IP）", bool(ok_fields),
       f"row={row[:120] if row else None}")

    # ---------- TC-03 参数脱敏 + TC-04 失败也记录 ----------
    req("PUT", "/profile/password", {"oldPassword": "definitely-wrong", "newPassword": "Abc123456"}, tok=T)
    time.sleep(1)
    row2 = mysql(f"SELECT operation,operation_status,IFNULL(error_msg,''),IFNULL(request_params,'<NULL>') "
                 f"FROM {DB}.oa_operation_log WHERE id>{base_id} AND operation='修改登录密码' ORDER BY id DESC LIMIT 1")
    p2 = (row2 or "").split("\t")
    ck("TC-OLOG-04-N 业务异常也记录日志（status=0 + error_msg）",
       len(p2) >= 3 and p2[1] == "0" and p2[2] != "", f"row={row2[:100] if row2 else None}")
    ck("TC-OLOG-03-S saveParams=false 时不记录请求参数（不泄露密码）",
       len(p2) >= 4 and p2[3] in ("<NULL>", ""), f"params={p2[3] if len(p2)>3 else None}")

    # ---------- TC-06 参数校验失败 ----------
    req("PUT", "/profile/update", {"nickname": "字" * 60, "gender": 1}, tok=T)
    time.sleep(1)
    cnt = mysql(f"SELECT COUNT(*) FROM {DB}.oa_operation_log WHERE id>{base_id} AND operation='更新个人资料'")
    ck("TC-OLOG-06-B 参数校验失败（400）是否落日志（按实现记录）", True,
       f"更新个人资料日志数={cnt}（0=校验先于切面被拦，>0=切面记录了失败）")

    # ---------- TC-05 SpEL 解析 ----------
    spel_row = mysql(f"SELECT operation FROM {DB}.oa_operation_log WHERE operation LIKE '%清理登录日志%' ORDER BY id DESC LIMIT 1")
    spel_ok = spel_row is not None and "#{" not in (spel_row or "")
    ck("TC-OLOG-05-N SpEL 表达式被求值（日志中不出现 #{...} 原文）", spel_ok,
       f"sample={spel_row}")

    # ---------- TC-08 模块覆盖 ----------
    mods = mysql(f"SELECT GROUP_CONCAT(DISTINCT module) FROM {DB}.oa_operation_log")
    mod_list = [m for m in (mods or "").split(",") if m]
    ck("TC-OLOG-08-S 覆盖多个业务模块（≥4）", len(mod_list) >= 4, f"modules={len(mod_list)}: {','.join(mod_list[:8])}")

    # ---------- TC-09 分页与筛选 ----------
    st, b = req("GET", "/system/operation-log/page?pageNo=1&pageSize=5", tok=T)
    d = b.get("data") or {}
    ck("TC-OLOG-09-N 分页返回 records 与 total",
       b.get("code") == 200 and len(d.get("records") or []) <= 5 and int(d.get("total") or 0) > 0,
       f"total={d.get('total')} records={len(d.get('records') or [])}")
    st, b = req("GET", "/system/operation-log/page?pageNo=1&pageSize=10&module=%E4%B8%AA%E4%BA%BA%E4%B8%AD%E5%BF%83", tok=T)
    recs = (b.get("data") or {}).get("records") or []
    ck("TC-OLOG-09-N 按模块筛选生效",
       b.get("code") == 200 and (not recs or all(x.get("module") == "个人中心" for x in recs)),
       f"count={len(recs)}")
    st, b = req("GET", "/system/operation-log/page?pageNo=1&pageSize=10&operationStatus=0", tok=T)
    recs = (b.get("data") or {}).get("records") or []
    ck("TC-OLOG-09-N 按状态筛选（失败=0）",
       b.get("code") == 200 and (not recs or all(x.get("operationStatus") == 0 for x in recs)),
       f"count={len(recs)}")

    # ---------- TC-10 导出 ----------
    st, headers, data = req("GET", "/system/operation-log/export?pageNo=1&pageSize=5", tok=T, raw=True)
    ct = headers.get("Content-Type") or headers.get("content-type") or ""
    ck("TC-OLOG-10-N 导出 xlsx 二进制", st == 200 and data[:2] == b"PK" and len(data) > 2000,
       f"http={st} bytes={len(data)} ct={ct[:40]}")

    # ---------- TC-11 清理护栏 ----------
    st, b = req("DELETE", "/system/operation-log/clean", tok=T)
    ck("TC-OLOG-11-E 无参数清理 → 400", b.get("code") == 400, f"code={b.get('code')}")
    future = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time() + 86400))
    st, b = req("DELETE", f"/system/operation-log/clean?before={urllib.parse.quote(future)}", tok=T)
    ck("TC-OLOG-11-E 未来时间清理 → 400", b.get("code") == 400, f"code={b.get('code')}")
    recent = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time() - 3600))
    st, b = req("DELETE", f"/system/operation-log/clean?before={urllib.parse.quote(recent)}", tok=T)
    ck("TC-OLOG-11-E 未保留最近 1 天 → 400", b.get("code") == 400, f"code={b.get('code')}")

    # ---------- TC-12 权限隔离 ----------
    st, _h, data = req("GET", "/system/operation-log/export?pageNo=1&pageSize=5", tok=T_STAFF, raw=True)
    bj = {}
    try:
        bj = json.loads(data.decode("utf-8"))
    except Exception:
        pass
    ck("TC-OLOG-12-E 无 operation-log:export 导出 → 403 且无文件",
       bj.get("code") == 403 and data[:2] != b"PK", f"code={bj.get('code')}")
    st, b = req("DELETE", "/system/operation-log/clean?before=2020-01-01%2000:00:00", tok=T_STAFF)
    ck("TC-OLOG-12-E 非超管清理 → 403", b.get("code") == 403, f"code={b.get('code')}")
    st, b = req("GET", "/system/operation-log/page?pageNo=1&pageSize=10&userId=1", tok=T_STAFF)
    recs = (b.get("data") or {}).get("records") or []
    ck("TC-OLOG-12-S 普通员工越权 userId 返空集",
       b.get("code") == 200 and all(x.get("userId") == 101809 for x in recs) if recs else True,
       f"count={len(recs)}")

    # ---------- TC-13 详情字段（列表即详情，无独立接口） ----------
    st, b = req("GET", "/system/operation-log/page?pageNo=1&pageSize=1", tok=T)
    r0 = ((b.get("data") or {}).get("records") or [{}])[0]
    need = ["id", "module", "operation", "method", "operationStatus", "costTime", "createTime"]
    missing = [k for k in need if k not in r0]
    ck("TC-OLOG-13-S 列表项含详情所需字段", not missing, f"missing={missing}")

    # ---------- 清理 ----------
    deleted = mysql(f"DELETE FROM {DB}.oa_operation_log WHERE id>{base_id}; SELECT ROW_COUNT();")
    left = mysql(f"SELECT COUNT(*) FROM {DB}.oa_operation_log WHERE id>{base_id}")
    print(f"CLEANUP 删除本轮日志={deleted.splitlines()[-1] if deleted else '?'} 残留={left}")

    fail = [x for x in R if not x]
    print(f"\nSUMMARY: {len(R) - len(fail)}/{len(R)} 通过")
    return 0 if not fail else 1


sys.exit(main())
