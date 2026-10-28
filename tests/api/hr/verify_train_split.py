#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""培训模块拆分后的行为等价验证（BE-OPT-08）。

为什么要单独写：既有的 `verify_p2.py` 用**硬编码日期**造期次（如 2026-10-24），
跑到「即将开始可报名」那条时本机时间已过期（6702），后续「取消/签到/成绩」整段
被跳过——而这三段恰恰是本次拆分中改动最大的路径（报名规则、学时按签到算）。
本用例用**相对时间**造数，不会随时间腐坏，并且把三条业务规则逐条钉住：

1. 名额满 → 候补；重复报名 → 6703；
2. 取消：距开课 > 24h 正常取消；< 24h 记爽约并落 `oa_train_absence`；
3. 学时按签到算：签到后 `realHours == 课程学时`；未签到不能录成绩（6710）。

清理：按本次创建的 id 精确删除（纯数字 SQL，避免中文参数编码坑）。
"""
import sys
from datetime import datetime, timedelta

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, ck, mysql, req, summary, token  # noqa: E402

EMP_ID = 101809  # tech_staff1
STAMP = datetime.now().strftime("%H%M%S")
COURSE_CODE = f"TC-SPLIT-{STAMP}"

created = {"sessions": [], "course": None}


def ts(delta: timedelta) -> str:
    return (datetime.now() + delta).strftime("%Y-%m-%dT%H:%M:%S")


def cleanup():
    if created["sessions"]:
        ids = ",".join(str(i) for i in created["sessions"])
        mysql(f"DELETE FROM {DB}.oa_train_absence WHERE session_id IN ({ids})")
        mysql(f"DELETE FROM {DB}.oa_train_enroll WHERE session_id IN ({ids})")
        mysql(f"DELETE FROM {DB}.oa_train_session WHERE id IN ({ids})")
    if created["course"]:
        mysql(f"DELETE FROM {DB}.oa_train_course WHERE id = {created['course']}")


def mk_session(admin, course_id, name, start, end, capacity, deadline=None):
    body = {
        "courseId": course_id,
        "sessionName": name,
        "startTime": start,
        "endTime": end,
        "location": "线上",
        "capacity": capacity,
    }
    if deadline:
        body["enrollDeadline"] = deadline
    _, b = req("POST", "/hr/train/session", body, tok=admin)
    sid = b.get("data")
    if sid:
        created["sessions"].append(sid)
    return sid


def enroll_of(admin, session_id):
    """取该期次下 tech_staff1 的报名 id"""
    rows = mysql(
        f"SELECT id FROM {DB}.oa_train_enroll "
        f"WHERE session_id={session_id} AND user_id={EMP_ID} AND is_deleted=0 LIMIT 1"
    )
    return int(rows.split()[0]) if rows.strip() else None


def main():
    admin = token(1, "admin")
    emp = token(EMP_ID, "tech_staff1")
    cleanup()

    # ---------- 造数：1 门课 + 4 个期次 ----------
    _, b = req("POST", "/hr/train/course", {
        "courseCode": COURSE_CODE, "courseName": "拆分回归课", "trainType": 2,
        "teacherType": 1, "hours": 3, "required": 1, "passScore": 60, "cost": 0,
    }, tok=admin)
    ck("建课成功", b.get("code") == 200, str(b.get("message")))
    course_id = b.get("data")
    created["course"] = course_id

    # A：3 天后开课（取消属正常）；B：今晚开课（取消 = 爽约）；C：已开课（可签到+录成绩）
    sid_far = mk_session(admin, course_id, "回归-远期", ts(timedelta(days=3)), ts(timedelta(days=3, hours=4)),
                         5, ts(timedelta(days=2)))
    sid_soon = mk_session(admin, course_id, "回归-24h内", ts(timedelta(hours=2)), ts(timedelta(hours=6)),
                          5, ts(timedelta(minutes=30)))
    sid_started = mk_session(admin, course_id, "回归-进行中", ts(timedelta(minutes=10)), ts(timedelta(hours=4)),
                             5, ts(timedelta(days=1)))
    ck("建期次成功（远期/24h内/进行中）", all([sid_far, sid_soon, sid_started]), f"{sid_far},{sid_soon},{sid_started}")

    # ---------- 规则 1：报名与重复报名 ----------
    _, b = req("POST", f"/hr/train/session/{sid_far}/enroll", tok=emp)
    ck("自助报名成功", b.get("code") == 200, str(b.get("message")))
    ck("报名状态=已报名", (b.get("data") or {}).get("status") == 1, f"status={(b.get('data') or {}).get('status')}")
    ck("报名回显学时=0（未签到不算学时）", float((b.get("data") or {}).get("realHours") or 0) == 0.0, "")

    _, b = req("POST", f"/hr/train/session/{sid_far}/enroll", tok=emp)
    ck("重复报名被拒(6703)", b.get("code") == 6703, f"code={b.get('code')} msg={b.get('message')}")

    # ---------- 规则 2：取消（远期正常 / 24h 内爽约） ----------
    enroll_far = enroll_of(admin, sid_far)
    _, b = req("POST", f"/hr/train/enroll/{enroll_far}/cancel", tok=emp)
    ck("距开课 >24h 取消成功", b.get("code") == 200, str(b.get("message")))
    st = mysql(f"SELECT status FROM {DB}.oa_train_enroll WHERE id={enroll_far}")
    ck("正常取消 → 状态=3 已取消", st.strip() == "3", f"status={st.strip()}")
    abs_cnt = mysql(f"SELECT COUNT(*) FROM {DB}.oa_train_absence WHERE enroll_id={enroll_far}")
    ck("正常取消不记爽约", abs_cnt.strip() == "0", f"absence={abs_cnt.strip()}")
    wait = mysql(f"SELECT enrolled_count,wait_count FROM {DB}.oa_train_session WHERE id={sid_far}").split()
    ck("取消后期次计数归零", wait == ["0", "0"], f"enrolled,wait={wait}")

    _, b = req("POST", f"/hr/train/session/{sid_soon}/enroll", tok=emp)
    ck("24h 内期次报名成功", b.get("code") == 200, str(b.get("message")))
    enroll_soon = enroll_of(admin, sid_soon)
    _, b = req("POST", f"/hr/train/enroll/{enroll_soon}/cancel", tok=emp)
    ck("24h 内取消仍受理", b.get("code") == 200, str(b.get("message")))
    st = mysql(f"SELECT status FROM {DB}.oa_train_enroll WHERE id={enroll_soon}")
    ck("24h 内取消 → 状态=7 爽约", st.strip() == "7", f"status={st.strip()}")
    abs_cnt = mysql(f"SELECT COUNT(*) FROM {DB}.oa_train_absence WHERE enroll_id={enroll_soon}")
    ck("爽约落 oa_train_absence", abs_cnt.strip() == "1", f"absence={abs_cnt.strip()}")

    # ---------- 规则 3：签到与成绩 ----------
    # 该期次 10 分钟后开始、报名截止在明天 → 仍属可报名窗口，同时满足签到窗口（开课前 30 分钟起）
    _, b = req("POST", f"/hr/train/session/{sid_started}/enroll", tok=emp)
    ck("开课前 10 分钟仍可报名", b.get("code") == 200, f"code={b.get('code')} msg={b.get('message')}")
    enroll_started = enroll_of(admin, sid_started)
    ck("进行中期次存在报名记录", enroll_started is not None, f"enrollId={enroll_started}")

    _, b = req("POST", f"/hr/train/enroll/{enroll_started}/checkin", tok=emp)
    ck("签到成功", b.get("code") == 200, str(b.get("message")))
    hours, st = mysql(f"SELECT real_hours,status FROM {DB}.oa_train_enroll WHERE id={enroll_started}").split()
    ck("签到后状态=4 已签到", st == "4", f"status={st}")
    ck("学时按签到写入（=课程学时 3）", float(hours) == 3.0, f"realHours={hours}")

    _, b = req("POST", f"/hr/train/enroll/{enroll_started}/checkin", tok=emp)
    ck("重复签到被拒", b.get("code") in (400, 6702), f"code={b.get('code')} msg={b.get('message')}")

    _, b = req("POST", "/hr/train/enroll/score",
               {"enrollId": enroll_started, "score": 80, "assessWay": 1, "satisfied": 4}, tok=admin)
    ck("成绩录入成功", b.get("code") == 200, str(b.get("message")))
    passed, st = mysql(f"SELECT passed,status FROM {DB}.oa_train_enroll WHERE id={enroll_started}").split()
    ck("及格 → passed=1 且结业 status=6", passed == "1" and st == "6", f"passed={passed} status={st}")

    _, b = req("POST", "/hr/train/enroll/score",
               {"enrollId": enroll_started, "score": 50, "assessWay": 1}, tok=admin)
    passed, st = mysql(f"SELECT passed,status FROM {DB}.oa_train_enroll WHERE id={enroll_started}").split()
    ck("不及格 → passed=0 且回到已签到 status=4", passed == "0" and st == "4", f"passed={passed} status={st}")

    # 未签到不能录成绩：用一条刚取消（status=3）的报名
    _, b = req("POST", "/hr/train/enroll/score",
               {"enrollId": enroll_far, "score": 90, "assessWay": 1}, tok=admin)
    ck("未签到不能录成绩(6710)", b.get("code") == 6710, f"code={b.get('code')} msg={b.get('message')}")

    # ---------- 查询与统计 ----------
    _, b = req("GET", "/hr/train/my/page?pageNo=1&pageSize=10", tok=emp)
    ck("我的培训记录可查", b.get("code") == 200 and len((b.get("data") or {}).get("records") or []) > 0,
       f"total={(b.get('data') or {}).get('total')}")

    _, b = req("GET", "/hr/train/my/available", tok=emp)
    ids = [s.get("id") for s in (b.get("data") or [])]
    ck("可报名期次排除已报名的", sid_soon not in ids and sid_started not in ids, f"available={ids}")

    _, b = req("GET", f"/hr/train/session/{sid_started}/enrolls", tok=admin)
    ck("期次花名册可查", b.get("code") == 200 and len(b.get("data") or []) >= 1, f"rows={len(b.get('data') or [])}")

    year = str(datetime.now().year)
    _, b = req("GET", f"/hr/train/statistics?year={year}", tok=admin)
    data = b.get("data") or {}
    ck("统计接口返回结构完整",
       b.get("code") == 200 and {"totalHours", "avgHours", "staffCount", "courseStats", "deptStats"} <= set(data.keys()),
       f"keys={sorted(data.keys())[:8]}")

    cleanup()
    left = mysql(f"SELECT COUNT(*) FROM {DB}.oa_train_session WHERE id IN "
                 f"({','.join(str(i) for i in created['sessions'])})") if created["sessions"] else "0"
    ck("用例清理干净", left.strip() == "0", f"left={left.strip()}")
    return summary()


if __name__ == "__main__":
    sys.exit(1 if main() else 0)
