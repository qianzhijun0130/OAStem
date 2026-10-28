#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""并发与幂等用例（D10 回归）。

D10：并发重复创建时，唯一键冲突落进兜底 `Exception` 分支返回 5000「系统内部错误」，
前端无法区分「重试即可」与「真的坏了」。本用例用真并发把它钉住：

- 期望（修复后）：恰好 1 条成功；其余是 5009（唯一键冲突）或对应业务码，**不能出现 5000**；
- 修复前：其余分支返回 5000 → 用例变红。

两个靶子，分别对应「业务表」与「系统表」：

1. **请假政策**（`uk_policy_city_type_year`）：服务层「先查后插」+ 唯一索引，最典型的竞态形态；
2. **字典类型**（`uk_dict_type`）：原始缺陷 `TC-X03-07` 的复现场景（8 并发同编码）。

清理：政策按年度列出后逐条删，字典按编码查页后删（不用 SQL 删，避开中文参数编码坑）。
"""
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import ck, req, summary, token  # noqa: E402

# 用不会被真实政策撞上的城市/年度，跑完即删
CITY = "并发校验城"
YEAR = 2099
PAYLOAD = {"city": CITY, "leaveType": 4, "days": 3, "effectiveYear": YEAR, "remark": "D10 并发用例"}

# 原始 D10 的复现场景：同一字典编码并发创建
DICT_CODE = "d10_concurrency_probe"
DICT_PAYLOAD = {"dictName": "D10 并发探针", "dictType": DICT_CODE, "remark": "D10 并发用例", "status": 1}


def cleanup(admin):
    """删除本用例可能创建的政策与字典（幂等：没数据也算成功）"""
    _, body = req("GET", f"/hr/leave/policy?year={YEAR}", tok=admin)
    for row in body.get("data") or []:
        if row.get("city") == CITY:
            req("DELETE", f"/hr/leave/policy/{row['id']}", tok=admin)
    cleanup_dict(admin)


def cleanup_dict(admin):
    _, body = req("GET", f"/system/dict/type/page?keyword={DICT_CODE}&pageNo=1&pageSize=20", tok=admin)
    for row in (body.get("data") or {}).get("records") or []:
        if row.get("dictType") == DICT_CODE:
            req("DELETE", f"/system/dict/type/{row['id']}", tok=admin)


def fire_concurrently(admin, path, payload, workers=4):
    """用 Barrier 让 N 个请求尽量同时进入服务端（否则串行执行看不到竞态）"""
    barrier = threading.Barrier(workers)

    def one(_):
        barrier.wait()
        _, body = req("POST", path, payload, tok=admin)
        return body.get("code"), body.get("message")

    with ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(one, range(workers)))


def main():
    admin = token(1, "admin")
    cleanup(admin)

    # ---- case 1：请假政策（业务表） ----
    results = fire_concurrently(admin, "/hr/leave/policy", PAYLOAD)
    codes = [c for c, _ in results]
    print(f"[policy codes] {codes}")
    print(f"[policy messages] {[m for _, m in results]}")

    ck("并发重复创建：恰好 1 条成功", codes.count(200) == 1, f"codes={codes}")
    ck("并发重复创建：不出现 5000（D10）", 5000 not in codes, f"codes={codes}")
    ck(
        "并发重复创建：其余为 5009 或 6311",
        all(c in (200, 5009, 6311) for c in codes),
        f"codes={codes}",
    )

    _, body = req("GET", f"/hr/leave/policy?year={YEAR}", tok=admin)
    landed = [r for r in (body.get("data") or []) if r.get("city") == CITY]
    ck("并发重复创建：落库恰好 1 行", len(landed) == 1, f"rows={len(landed)}")

    # ---- case 2：字典类型（原始 D10 复现场景，8 并发） ----
    dict_results = fire_concurrently(admin, "/system/dict/type", DICT_PAYLOAD, workers=8)
    dict_codes = [c for c, _ in dict_results]
    print(f"[dict codes] {dict_codes}")
    print(f"[dict messages] {[m for _, m in dict_results]}")

    ck("字典并发创建：恰好 1 条成功", dict_codes.count(200) == 1, f"codes={dict_codes}")
    ck("字典并发创建：不出现 5000（D10 原始场景）", 5000 not in dict_codes, f"codes={dict_codes}")
    ck(
        "字典并发创建：其余为 5009 或 2302",
        all(c in (200, 5009, 2302) for c in dict_codes),
        f"codes={dict_codes}",
    )

    cleanup(admin)
    _, body = req("GET", f"/hr/leave/policy?year={YEAR}", tok=admin)
    left = [r for r in (body.get("data") or []) if r.get("city") == CITY]
    _, body = req("GET", f"/system/dict/type/page?keyword={DICT_CODE}&pageNo=1&pageSize=20", tok=admin)
    left_dict = [r for r in (body.get("data") or {}).get("records") or [] if r.get("dictType") == DICT_CODE]
    ck("用例清理干净", len(left) == 0 and len(left_dict) == 0, f"policy={len(left)} dict={len(left_dict)}")

    return summary()


if __name__ == "__main__":
    sys.exit(1 if main() else 0)
