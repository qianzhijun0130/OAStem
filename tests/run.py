#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试总入口：刷新令牌 → 依次跑接口脚本（可选 UI 脚本）→ 汇总。

用法：
  python tests/run.py                 # 跑全部接口脚本
  python tests/run.py --only hr/      # 只跑路径含 hr/ 的
  python tests/run.py --ui            # 接口 + UI（需前端 dev server 已启动）
  python tests/run.py --list          # 只列出可跑项

前置：后端 8080 已启动；跑 UI 还需前端 3000 已启动。
令牌每次现签（8 小时有效），不再依赖手工维护的令牌文件——这是 QA-OPT-01 要解决的坑之一。
"""
import os, subprocess, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent
RUNTIME = ROOT / ".runtime"
RUNTIME.mkdir(exist_ok=True)
TOKEN_FILE = RUNTIME / "admin_token.txt"


def refresh_token():
    """用 tests/api/lib/xlib.py 现签一个超管令牌，供 UI 脚本读取。"""
    sys.path.insert(0, str(ROOT / "api" / "lib"))
    import xlib  # noqa: E402
    TOKEN_FILE.write_text(xlib.token(1, "admin"), encoding="utf-8")
    return TOKEN_FILE


def discover(ui: bool):
    items = sorted(p for p in (ROOT / "api").rglob("*.py") if "lib" not in p.parts)
    if ui:
        items += sorted(p for p in (ROOT / "ui").rglob("*.js") if "lib" not in p.parts)
    return items


def main():
    args = sys.argv[1:]
    only = None
    if "--only" in args:
        only = args[args.index("--only") + 1]
    with_ui = "--ui" in args
    items = discover(with_ui)
    if only:
        items = [p for p in items if only in str(p.relative_to(ROOT)).replace("\\", "/")]
    if "--list" in args:
        for p in items:
            print(p.relative_to(ROOT))
        return 0

    try:
        refresh_token()
        print(f"[token] {TOKEN_FILE}")
    except Exception as e:  # noqa: BLE001
        print(f"[token] 刷新失败（UI 脚本会受影响）：{e}")

    passed, failed = [], []
    for p in items:
        rel = str(p.relative_to(ROOT)).replace("\\", "/")
        print(f"\n===== {rel} =====")
        if p.suffix == ".py":
            cmd = [sys.executable, str(p)]
        else:
            cmd = [os.environ.get("OA_NODE", "node"), str(p)]
        r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True, encoding="utf-8", errors="replace")
        tail = [l for l in (r.stdout or "").splitlines() if l.startswith(("SUMMARY", "FAILED"))]
        print("\n".join(tail) if tail else (r.stdout or "")[-600:])
        if r.returncode == 0:
            passed.append(rel)
        else:
            failed.append(rel)
            if not tail:
                print((r.stderr or "")[-600:])

    print(f"\n########## 总计：{len(passed)} 通过 / {len(failed)} 失败 / 共 {len(items)} ##########")
    if failed:
        print("失败项：" + " ; ".join(failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
