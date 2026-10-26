#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Redis 接入验证：
A 登出黑名单（登出后旧 token 立刻 401，Redis 有对应 key 与剩余 TTL）
B 字典缓存（首次查库并写缓存、TTL 正确、写操作主动失效）
C 登录失败限流（连错 5 次锁定，第 6 次 1008，清理后可正常登录）
"""
import base64, hashlib, hmac, json, os, re, subprocess, sys, time, urllib.error, urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:8080"
REDIS_CLI = r"D:/Program Files/Redis/redis-cli.exe"
PROPS = [Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application.properties"),
         Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application-local.properties")]
MYSQL = r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe"
DB = "oa_system"
R = []


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


def resolve(value, fallback=""):
    """展开 ${VAR:default} 形式的占位符，避免把字面量当成真实值传给 mysql"""
    if not value:
        return fallback
    m = re.fullmatch(r"\$\{([A-Za-z0-9_]+)(?::([^}]*))?\}", value.strip())
    if m:
        return os.environ.get(m.group(1)) or (m.group(2) if m.group(2) is not None else fallback)
    return value


PD = load_props()
SECRET = PD["jwt.secret"].encode("utf-8")
DB_USER = resolve(PD.get("spring.datasource.username"), "root")
DB_PASS = resolve(PD.get("spring.datasource.password"), "")


def mysql(sql):
    return subprocess.run([MYSQL, "--force", f"-u{DB_USER}", "-N", "-e", sql],
                          capture_output=True, text=True,
                          env=dict(os.environ, MYSQL_PWD=DB_PASS)).stdout.strip()


def redis(*args):
    out = subprocess.run([REDIS_CLI, "-p", "6379", *args], capture_output=True, text=True)
    return out.stdout.strip()


def b64(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def make_token(uid, name, hours=8):
    now = int(time.time())
    perms = [x for x in mysql(
        f"SELECT menu_code FROM {DB}.oa_menu WHERE menu_code IS NOT NULL AND menu_code<>''").splitlines() if x]
    payload = {"sub": name, "userId": uid, "roles": ["SUPER_ADMIN"], "perms": perms,
               "iat": now, "exp": now + hours * 3600}
    h = b64(json.dumps({"alg": "HS384", "typ": "JWT"}, separators=(",", ":")).encode())
    b = b64(json.dumps(payload, separators=(",", ":")).encode())
    sig = b64(hmac.new(SECRET, f"{h}.{b}".encode(), hashlib.sha384).digest())
    return f"{h}.{b}.{sig}"


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def ck(name, ok, detail=""):
    R.append((name, ok))
    print(f"{'PASS' if ok else 'FAIL'} | {name} | {detail}")


def main():
    # ---------- A 登出黑名单 ----------
    token_a = make_token(1, "admin")
    token = token_a
    st, body = req("GET", "/auth/info", token=token)
    ck("A1 登出前 token 可用", st == 200 and body.get("code") == 200, f"http={st} code={body.get('code')}")

    st, body = req("POST", "/auth/logout", token=token)
    ck("A2 登出接口返回成功", st == 200 and body.get("code") == 200, f"http={st}")

    st, body = req("GET", "/auth/info", token=token)
    ck("A3 登出后同一 token 立即失效（401）", st == 401 or body.get("code") == 401,
       f"http={st} code={body.get('code')}")

    keys = [k for k in redis("keys", "oastem:auth:blacklist:*").splitlines() if k]
    ttl = redis("ttl", keys[0]) if keys else "-1"
    ok_ttl = ttl.isdigit() and 0 < int(ttl) <= 8 * 3600
    ck("A4 Redis 存在黑名单 key 且 TTL 为剩余寿命", bool(keys) and ok_ttl,
       f"keys={len(keys)} ttl={ttl}")

    # 换用不同有效期以生成不同令牌：同一秒内相同 payload 会得到完全一样的 token 字符串
    st, body = req("GET", "/auth/info", token=make_token(1, "admin", hours=7))
    ck("A5 其他未登出 token 不受影响", st == 200, f"http={st}")

    # ---------- B 字典缓存 ----------
    # A 段已把 token 登出（进黑名单）；同一秒内相同 payload 会生成完全相同的 token，
    # 因此这里用不同有效期强制生成一个「另一个」令牌
    token = make_token(1, "admin", hours=6)
    redis("del", "oastem:dict:items:flow_category")
    st, body = req("GET", "/system/dict/data/type/flow_category", token=token)
    rows = body.get("data") or []
    ck("B1 字典接口正常返回", st == 200 and len(rows) > 0, f"count={len(rows)}")

    exists = redis("exists", "oastem:dict:items:flow_category")
    ttl = redis("ttl", "oastem:dict:items:flow_category")
    ck("B2 首次查询已写入缓存", exists == "1", f"exists={exists}")
    ck("B3 缓存 TTL 取自配置（<=600s）", ttl.isdigit() and 0 < int(ttl) <= 600, f"ttl={ttl}")

    st, body2 = req("GET", "/system/dict/data/type/flow_category", token=token)
    same = [r.get("itemValue") for r in (body2.get("data") or [])] == [r.get("itemValue") for r in rows]
    ck("B4 二次查询内容一致（命中缓存）", st == 200 and same, f"same={same}")

    # 通过接口改一项，验证主动失效
    target = rows[0]
    payload = {"id": target["id"], "dictType": target["dictType"], "itemLabel": target["itemLabel"],
               "itemValue": target["itemValue"], "sort": target.get("sort"),
               "cssClass": target.get("cssClass"), "isDefault": target.get("isDefault"),
               "status": target.get("status"), "remark": target.get("remark")}
    st, w = req("POST", "/system/dict/data", payload, token=token)
    evicted = redis("exists", "oastem:dict:items:flow_category")
    ck("B5 字典写操作后缓存被清除", st == 200 and w.get("code") == 200 and evicted == "0",
       f"http={st} code={w.get('code')} msg={w.get('message')} exists={evicted}")

    st, body3 = req("GET", "/system/dict/data/type/flow_category", token=token)
    ck("B6 失效后重新查询仍返回正确数据", st == 200 and len(body3.get("data") or []) == len(rows),
       f"count={len(body3.get('data') or [])}")

    # ---------- C 登录失败限流 ----------
    redis("del", "oastem:auth:login:fail:admin", "oastem:auth:login:lock:admin")
    codes = []
    for _ in range(5):
        _, b = req("POST", "/auth/login", {"username": "admin", "password": "definitely-wrong"})
        codes.append(b.get("code"))
    st, blk = req("POST", "/auth/login", {"username": "admin", "password": "Admin@123456"})
    ck("C1 连错 5 次后第 6 次被限流拦截（1008）",
       codes == [1001] * 5 and blk.get("code") == 1008,
       f"codes={codes} 6th={blk.get('code')}")

    lock_ttl = redis("ttl", "oastem:auth:login:lock:admin")
    ck("C2 锁定 key 存在且 TTL 符合配置（<=300s）",
       lock_ttl.isdigit() and 0 < int(lock_ttl) <= 300, f"ttl={lock_ttl}")

    # 清理后应能正常登录
    redis("del", "oastem:auth:login:fail:admin", "oastem:auth:login:lock:admin")
    st, ok = req("POST", "/auth/login", {"username": "admin", "password": "Admin@123456"})
    ck("C3 清除计数后正常登录成功", ok.get("code") == 200 and bool(ok.get("data", {}).get("token")),
       f"code={ok.get('code')}")

    # ---------- 收尾 ----------
    # 清掉本次测试注册的黑名单与限流键（避免污染后续测试/正常使用）
    for k in [k for k in redis("keys", "oastem:auth:*").splitlines() if k]:
        redis("del", k)
    redis("del", "oastem:dict:items:flow_category")
    cleaned = mysql(f"DELETE FROM {DB}.oa_login_log WHERE login_status=0 "
                    f"AND create_time >= NOW() - INTERVAL 10 MINUTE; SELECT ROW_COUNT();")
    print("清理登录失败日志:", cleaned.splitlines()[-1] if cleaned else "0")
    left = redis("keys", "oastem:*").splitlines()
    print("残留 auth 键:", [k for k in left if k] or "无")

    fail = [n for n, ok in R if not ok]
    print(f"\nSUMMARY: {len(R) - len(fail)}/{len(R)} 通过")
    return 0 if not fail else 1


sys.exit(main())
