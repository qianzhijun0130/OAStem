#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""横切专项（X / X2）共用测试工具：令牌签发、DB 查询、HTTP 调用、断言计数。

设计要点：
- 令牌按「该用户真实角色对应的菜单权限码」签发，避免出现「token 里有全部权限」的假放行；
- 所有 HTTP 调用统一 `req(method, path, body=None, tok=None)`，token 一律用关键字传参
  （M14 曾因位置传参把 token 当 body 发出去，导致全量 401，排查浪费很久）。
"""
import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = os.environ.get("OA_BASE", "http://127.0.0.1:8080")
PROPS = [
    Path(__file__).resolve().parents[3] / "oa-system" / "src" / "main" / "resources" / "application.properties",
    Path(__file__).resolve().parents[3] / "oa-system" / "src" / "main" / "resources" / "application-local.properties",
]
MYSQL = os.environ.get("OA_MYSQL", r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe")
REDIS = r"D:/Program Files/Redis/redis-cli.exe"
DB = "oa_system"

_R = []


def _load_props():
    d = {}
    for p in PROPS:
        if p.exists():
            for line in p.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    d[k.strip()] = v.strip()
    return d


def _resolve(value, fallback=""):
    """展开 ${VAR:default}，避免把字面量当真实值（曾导致权限码查询静默返回空集）。"""
    if not value:
        return fallback
    m = re.fullmatch(r"\$\{([A-Za-z0-9_]+)(?::([^}]*))?\}", value.strip())
    if m:
        return os.environ.get(m.group(1)) or (m.group(2) if m.group(2) is not None else fallback)
    return value


_PD = _load_props()
SEC = _PD["jwt.secret"].encode("utf-8")
DB_USER = _resolve(_PD.get("spring.datasource.username"), "root")
DB_PASS = _resolve(_PD.get("spring.datasource.password"), "")


def mysql(sql):
    out = subprocess.run([MYSQL, "--force", f"-u{DB_USER}", "-N", "-e", sql],
                         capture_output=True, text=True, env=dict(os.environ, MYSQL_PWD=DB_PASS))
    return out.stdout.strip()


def mysql_exec(sql):
    """执行写操作（DELETE/INSERT/UPDATE），失败抛异常。

    `mysql()` 带 `--force` 且只取 stdout——写失败会被完全吞掉。造夹具时用这个：
    否则「清场没清掉 + 插入撞唯一索引」会双双静默，脚本照常往下跑，
    测的是上一轮的残留数据，断言全绿却什么都没验证（本轮就踩了这个坑）。
    """
    out = subprocess.run([MYSQL, f"-u{DB_USER}", DB, "-N", "-e", sql],
                         capture_output=True, text=True, env=dict(os.environ, MYSQL_PWD=DB_PASS))
    if out.returncode != 0:
        raise RuntimeError(f"SQL 失败: {out.stderr.strip()[:300]} | SQL: {sql[:200]}")
    return out.stdout.strip()


def redis(*args):
    return subprocess.run([REDIS, *args], capture_output=True, text=True).stdout.strip()


def b64(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def perms_of(user_id):
    """该用户「角色 → 菜单」合并后的权限码集合（与后端 /auth/info 同口径）。"""
    rows = mysql(
        f"SELECT DISTINCT m.menu_code FROM {DB}.oa_menu m "
        f"JOIN {DB}.oa_role_menu rm ON rm.menu_id=m.id "
        f"JOIN {DB}.oa_user_role ur ON ur.role_id=rm.role_id "
        f"WHERE ur.user_id={user_id} AND m.menu_code IS NOT NULL AND m.menu_code<>''")
    return [x for x in rows.splitlines() if x]


def roles_of(user_id):
    rows = mysql(f"SELECT DISTINCT r.role_code FROM {DB}.oa_role r "
                 f"JOIN {DB}.oa_user_role ur ON ur.role_id=r.id WHERE ur.user_id={user_id}")
    return [x for x in rows.splitlines() if x]


def token(user_id, username, perms=None, hours=2, extra=None):
    perms = perms_of(user_id) if perms is None else perms
    now = int(time.time())
    payload = {"sub": username, "userId": user_id, "roles": roles_of(user_id) or ["SUPER_ADMIN"],
               "perms": perms, "iat": now, "exp": now + hours * 3600}
    if extra:
        payload.update(extra)
    h = b64(json.dumps({"alg": "HS384", "typ": "JWT"}, separators=(",", ":")).encode())
    b = b64(json.dumps(payload, separators=(",", ":")).encode())
    return f"{h}.{b}.{b64(hmac.new(SEC, f'{h}.{b}'.encode(), hashlib.sha384).digest())}"


def req(method, path, body=None, tok=None, raw=False, timeout=30, headers=None):
    """返回 (http_status, body)。raw=True 时 body 是 bytes。"""
    data = None
    hd = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
    if tok:
        hd["Authorization"] = "Bearer " + tok
    if headers:
        hd.update(headers)
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=hd)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as x:
            payload = x.read()
            return x.status, payload if raw else json.loads(payload.decode() or "{}")
    except urllib.error.HTTPError as e:
        payload = e.read()
        if raw:
            return e.code, payload
        try:
            return e.code, json.loads(payload.decode() or "{}")
        except Exception:
            return e.code, {}


def upload(path, filename, content, tok=None, timeout=120, field="file"):
    """multipart/form-data 上传，返回 (http_status, body)。

    `req()` 只会发 JSON，而 Excel 导入类接口要 multipart。这里手写边界体，
    不引第三方依赖；filename 用纯 ASCII，避免中文文件名在头部编码上踩坑。
    """
    boundary = "----OAStemTestBoundary7d3a"
    head = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
        "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n"
    ).encode()
    tail = f"\r\n--{boundary}--\r\n".encode()
    data = head + content + tail
    hd = {"Content-Type": f"multipart/form-data; boundary={boundary}"}
    if tok:
        hd["Authorization"] = "Bearer " + tok
    r = urllib.request.Request(BASE + path, data=data, method="POST", headers=hd)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as x:
            return x.status, json.loads(x.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {}


def ck(name, ok, detail=""):
    _R.append((name, bool(ok)))
    print(f"{'PASS' if ok else 'FAIL'} | {name} | {detail}")
    return ok


def summary():
    fail = [n for n, ok in _R if not ok]
    print(f"\nSUMMARY: {len(_R) - len(fail)}/{len(_R)} 通过")
    if fail:
        print("FAILED: " + " ; ".join(fail))
    return 0 if not fail else 1
