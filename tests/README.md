# tests — 可执行测试资产

> 与 `开发文档/`、`测试文档/` 的区别：那两处是**过程产物**（执行记录、截图、日志），不入库；
> 这里是**工程资产**（runner、断言、造数、清理）与**契约资产**（用例定义），需要长期演进，故入库。

## 目录

```
tests/
  run.py                  # 总入口：刷新令牌 → 跑脚本 → 汇总
  api/                    # 接口自动化（Python 3.13）
    lib/xlib.py           # 公共库：令牌签发 / DB 查询 / HTTP / 断言计数
    hr/                   # 人力资源：档案考勤、假勤、薪酬、绩效、招聘培训、加密、数据范围
    system/               # 系统：日志、文件、Redis、强制下线
    x/                    # 横切：权限、性能、路由挑战、超时
  ui/                     # UI 自动化（Node + playwright-core）
    lib/browser.js        # 启动 / 登录 / 报错收集 / 断言汇总
    hr/  system/          # 按模块组织的页面断言
  cases/                  # 用例定义（TC-*），从「测试文档/」迁入的契约资产
  .runtime/               # 执行产物（令牌、截图），已 gitignore
```

## 运行

```bash
# 前置：后端 8080 已启动；跑 UI 还需前端 3000
python tests/run.py                 # 全部接口脚本
python tests/run.py --only hr/      # 只跑 hr 目录
python tests/run.py --ui            # 接口 + UI
python tests/run.py --list          # 列出可跑项
```

退出码：全部通过 0，任一项失败 1——可直接接 CI。

## 环境变量

| 变量 | 作用 | 默认 |
| --- | --- | --- |
| `OA_BASE` | 后端地址 | `http://127.0.0.1:8080` |
| `OA_MYSQL` | mysql 客户端路径 | `D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe` |
| `OA_NODE` | runner 调 UI 脚本用的 node | `node` |
| `PLAYWRIGHT_CORE` | playwright-core 绝对路径（常规解析失败时回退） | 本机 workbuddy 目录 |
| `OA_BROWSER` | 浏览器可执行文件 | 本机 Edge |
| `OA_TOKEN_FILE` | UI 脚本读取的令牌文件 | `tests/.runtime/admin_token.txt` |

## 两条纪律

1. **令牌每次现签**：`run.py` 启动时用 `xlib.token()` 重新签发并写入 `.runtime/`，
   UI 脚本只读该文件。此前 UI 脚本依赖手工维护的 `oastem_admin_token.txt`，
   过期后取值全空，曾导致「界面回归」误判。
2. **造数走接口，不要用 `mysql -e` 写中文**：`mysql -e` 写中文会静默失败（本项目已知坑），
   需要写中文时用 `--default-character-set=utf8mb4` + stdin 重定向。

## 脚本约定

- 每个脚本自带清理：开头清残留、结尾还原数据，跑到一半失败也不会污染下一轮。
- 断言必须比「HTTP 200 + code 200」更强：金额、天数、状态联动、副作用落库。
- 输出统一 `PASS | 名称 | 详情` 与 `SUMMARY: n/m 通过`，`run.py` 据此聚合。
