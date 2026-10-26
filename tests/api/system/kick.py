#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""管理员强制下线：接口级验证。

覆盖：
1. 登录后出现在在线列表；
2. 踢出后旧令牌立即 401（且 message 能说明原因）；
3. 踢出只影响目标用户，管理员自己不受影响；
4. 重新登录拿到的新令牌可用（不是把账号封了）；
5. 异常分支：踢自己 / 踢已下线用户 / 未登录调用。
"""
import sys

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import ck, req, summary  # noqa: E402

BASE_USERS = {"admin": "Admin@123456", "tech_staff1": "qwe@123456"}


def login(username, password):
    _, body = req("POST", "/auth/login", {"username": username, "password": password})
    return (body.get("data") or {}).get("token"), body


def online_ids(tok):
    _, body = req("GET", "/system/online/page?pageNo=1&pageSize=50", tok=tok)
    rows = ((body.get("data") or {}).get("records")) or []
    return {r["username"]: r["userId"] for r in rows}


def main():
    admin_tok, admin_body = login("admin", BASE_USERS["admin"])
    ck("管理员登录成功", bool(admin_tok), f"code={admin_body.get('code')}")

    victim_tok, victim_body = login("tech_staff1", BASE_USERS["tech_staff1"])
    ck("被踢用户登录成功", bool(victim_tok), f"code={victim_body.get('code')}")

    ids = online_ids(admin_tok)
    ck("在线列表包含管理员与被踢用户",
       "admin" in ids and "tech_staff1" in ids, f"online={list(ids)}")
    victim_id = ids.get("tech_staff1")

    # 确认被踢用户当前可用
    status, body = req("GET", "/profile/info", tok=victim_tok)
    ck("踢出前被踢用户令牌可用", body.get("code") == 200, f"code={body.get('code')}")

    # ---------- 踢出 ----------
    status, body = req("POST", f"/system/online/{victim_id}/kick", tok=admin_tok)
    ck("强制下线接口返回成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")

    status, body = req("GET", "/profile/info", tok=victim_tok)
    ck("被踢用户旧令牌立即失效（HTTP 401）", status == 401 and body.get("code") == 401,
       f"http={status} code={body.get('code')}")
    ck("401 文案说明是被强制下线", "强制下线" in (body.get("message") or ""), body.get("message"))

    status, body = req("GET", "/profile/info", tok=admin_tok)
    ck("管理员自己的令牌不受影响", body.get("code") == 200, f"code={body.get('code')}")

    ids_after = online_ids(admin_tok)
    ck("在线列表已移除被踢用户", "tech_staff1" not in ids_after, f"online={list(ids_after)}")

    # ---------- 重新登录可用 ----------
    new_tok, new_body = login("tech_staff1", BASE_USERS["tech_staff1"])
    status, body = req("GET", "/profile/info", tok=new_tok)
    ck("被踢用户重新登录后新令牌可用", bool(new_tok) and body.get("code") == 200,
       f"code={body.get('code')}")

    # ---------- 异常分支 ----------
    status, body = req("POST", "/system/online/1/kick", tok=admin_tok)
    ck("踢自己 → 1115 拒绝", body.get("code") == 1115, f"code={body.get('code')} msg={body.get('message')}")

    # 找一个肯定没登录过的用户
    from xlib import mysql
    offline_id = mysql("SELECT id FROM oa_system.oa_user WHERE username='hr_staff1' LIMIT 1").strip()
    status, body = req("POST", f"/system/online/{offline_id}/kick", tok=admin_tok)
    ck("踢已下线用户 → 1114", body.get("code") == 1114, f"code={body.get('code')} msg={body.get('message')}")

    status, body = req("POST", "/system/online/999999/kick", tok=admin_tok)
    ck("踢不存在用户 → 1004", body.get("code") == 1004, f"code={body.get('code')} msg={body.get('message')}")

    status, body = req("GET", "/system/online/page?pageNo=1&pageSize=10")
    ck("未登录访问在线列表 → 401", status == 401 or body.get("code") == 401, f"http={status}")

    # ---------- 收尾：把新令牌登出 ----------
    req("POST", "/auth/logout", tok=new_tok)
    req("POST", "/auth/logout", tok=admin_tok)
    return summary()


if __name__ == "__main__":
    sys.exit(main())
