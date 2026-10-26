#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""人力资源 P0-3 假勤验证：额度 / 请假 / 加班 / 出差 / 补卡 + 审批回调。

前置：HR_LEAVE 流程已发布（测试开始时自动导入并检查），否则发起类用例会返回 6207。
收尾：删除业务行、审批实例与 Flowable 运行/历史数据，并把额度复原。
"""
import json
import sys
from urllib.parse import quote

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
from xlib import ck, mysql, req, summary, token  # noqa: E402

USER_ID = 101809          # tech_staff1
YEAR = 2026
STATE = {"instances": [], "leaves": [], "requests": []}


def ensure_flow(admin):
    """确保 HR_LEAVE 流程已发布：没有就导入内置 BPMN（幂等）。"""
    code, body = req("GET", "/workflow/flow-definition/options", tok=admin)
    options = body.get("data") or []
    if any((o.get("flowCode") == "HR_LEAVE") for o in options):
        return
    xml = open("F:/GitHub/OAStem/oa-system/src/main/resources/processes/leave.bpmn20.xml", encoding="utf-8").read()
    req("POST", "/workflow/flow-definition/import",
        {"bpmnXml": xml, "flowCode": "HR_LEAVE", "flowName": "请假申请", "category": "hr"}, tok=admin)


def find_task(admin, instance_id):
    """按审批实例查引擎里的活动任务。

    不能用 /my-todo：待办只列「本人是候选人」的任务，而本流程的候选人是部门主管，
    超管能看到并不等于出现在待办里（超管的能力是「可处理任意任务」）。
    """
    if not instance_id:
        return None
    proc = mysql(f"SELECT IFNULL(proc_inst_id,'') FROM oa_system.oa_approval_instance "
                 f"WHERE id={instance_id}").strip()
    if not proc:
        return None
    task = mysql(f"SELECT IFNULL(ID_,'') FROM oa_system.act_ru_task WHERE PROC_INST_ID_='{proc}' LIMIT 1").strip()
    return task or None


def approve(admin, instance_id, action="approve", opinion="自动化测试", times=1):
    """审批；times>1 时连续审批多个节点（请假是 主管 → 人事 两节点）。"""
    body = {"code": None, "message": "未找到待办任务: instance=" + str(instance_id)}
    for _ in range(max(times, 1)):
        task_id = find_task(admin, instance_id)
        if not task_id:
            break
        code, body = req("POST", "/workflow/approval/complete",
                         {"taskId": task_id, "action": action, "opinion": opinion}, tok=admin)
        if body.get("code") != 200 or action != "approve":
            break
    return body


def quota_of(tok, leave_type):
    code, body = req("GET", f"/hr/leave/quota/my?year={YEAR}", tok=tok)
    for q in (body.get("data") or []):
        if q.get("leaveType") == leave_type:
            return q
    return {}


def main():
    admin = token(1, "admin", hours=2)
    user = token(USER_ID, "tech_staff1", hours=2)
    ensure_flow(admin)

    # ================= 额度 =================
    code, body = req("GET", f"/hr/leave/quota/my?year={YEAR}", tok=user)
    ck("我的额度自动初始化（年假/调休）", body.get("code") == 200
       and {3, 8}.issubset({q.get("leaveType") for q in (body.get("data") or [])}),
       str([q.get("leaveType") for q in (body.get("data") or [])]))

    code, body = req("POST", "/hr/leave/quota/adjust",
                     {"userId": USER_ID, "leaveType": 3, "year": YEAR,
                      "totalDays": 5, "remark": "P0-3 自动化测试授予 5 天年假"}, tok=admin)
    ck("HR 调整年假额度为 5 天", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")
    ck("额度台账显示可用 5 天", float(quota_of(user, 3).get("availableDays") or 0) == 5.0,
       str(quota_of(user, 3)))

    code, body = req("POST", "/hr/leave/quota/adjust",
                     {"userId": USER_ID, "leaveType": 3, "year": YEAR, "totalDays": 6}, tok=admin)
    ck("调整额度不填说明被拒", body.get("code") == 400, f"code={body.get('code')}")

    # ================= 请假 =================
    code, body = req("POST", "/hr/leave",
                     {"leaveType": 3, "startTime": "2026-10-26T09:00:00", "endTime": "2026-10-26T18:00:00",
                      "leaveDays": 1, "reason": "自动化测试-年假"}, tok=user)
    leave_id = body.get("data")
    ck("提交年假 1 天成功", body.get("code") == 200 and leave_id,
       f"code={body.get('code')} msg={body.get('message')}")
    if leave_id:
        STATE["leaves"].append(leave_id)
    ck("提交后额度被占用（locked=1）", float(quota_of(user, 3).get("lockedDays") or 0) == 1.0,
       str(quota_of(user, 3)))

    code, body = req("POST", "/hr/leave",
                     {"leaveType": 1, "startTime": "2026-10-26T14:00:00", "endTime": "2026-10-26T18:00:00",
                      "leaveDays": 0.5, "reason": "自动化测试-重叠"}, tok=user)
    ck("同期重叠请假被拒（6302）", body.get("code") == 6302, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("POST", "/hr/leave",
                     {"leaveType": 3, "startTime": "2026-11-02T09:00:00", "endTime": "2026-11-06T18:00:00",
                      "leaveDays": 5, "reason": "自动化测试-超额"}, tok=user)
    ck("额度不足被拒（6301，剩 4 天）", body.get("code") == 6301, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("POST", "/hr/leave",
                     {"leaveType": 2, "startTime": "2026-11-09T09:00:00", "endTime": "2026-11-12T18:00:00",
                      "leaveDays": 4, "reason": "自动化测试-病假无证明"}, tok=user)
    ck("病假 ≥3 天无证明被拒（6307）", body.get("code") == 6307, f"code={body.get('code')} msg={body.get('message')}")

    code, body = req("POST", "/hr/leave",
                     {"leaveType": 4, "startTime": "2026-11-16T09:00:00", "endTime": "2026-11-23T18:00:00",
                      "leaveDays": 8, "reason": "自动化测试-超法定婚假"}, tok=user)
    ck("婚假超法定上限被拒（6305）", body.get("code") == 6305, f"code={body.get('code')} msg={body.get('message')}")

    # 审批通过
    code, body = req("GET", f"/hr/leave/{leave_id}", tok=user)
    bill = (body.get("data") or {}).get("billCode")
    inst = (body.get("data") or {}).get("instanceId")
    ck("请假单带出单据编号与实例ID", bool(bill) and bool(inst), f"bill={bill} instance={inst}")
    if inst:
        STATE["instances"].append(inst)

    body = approve(admin, inst, times=3)
    ck("主管审批通过", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")
    code, body = req("GET", f"/hr/leave/{leave_id}", tok=user)
    d = body.get("data") or {}
    ck("回调：业务状态变为已通过", d.get("leaveStatus") == 2, f"status={d.get('leaveStatus')}")
    q = quota_of(user, 3)
    ck("回调：占用转已用（used=1 / locked=0）",
       float(q.get("usedDays") or 0) == 1.0 and float(q.get("lockedDays") or 0) == 0.0, str(q))

    # 驳回
    code, body = req("POST", "/hr/leave",
                     {"leaveType": 3, "startTime": "2026-10-27T09:00:00", "endTime": "2026-10-27T18:00:00",
                      "leaveDays": 1, "reason": "自动化测试-待驳回"}, tok=user)
    reject_id = body.get("data")
    if reject_id:
        STATE["leaves"].append(reject_id)
    code, body = req("GET", f"/hr/leave/{reject_id}", tok=user)
    rd = body.get("data") or {}
    if rd.get("instanceId"):
        STATE["instances"].append(rd["instanceId"])
    body = approve(admin, rd.get("instanceId"), action="reject", opinion="自动化测试驳回")
    ck("主管驳回成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")
    code, body = req("GET", f"/hr/leave/{reject_id}", tok=user)
    ck("回调：驳回后状态为 3", (body.get("data") or {}).get("leaveStatus") == 3,
       str((body.get("data") or {}).get("leaveStatus")))
    ck("回调：驳回释放占用（locked=0）", float(quota_of(user, 3).get("lockedDays") or 0) == 0.0,
       str(quota_of(user, 3)))

    # 撤销
    code, body = req("POST", "/hr/leave",
                     {"leaveType": 3, "startTime": "2026-10-28T09:00:00", "endTime": "2026-10-28T18:00:00",
                      "leaveDays": 1, "reason": "自动化测试-待撤销"}, tok=user)
    cancel_id = body.get("data")
    if cancel_id:
        STATE["leaves"].append(cancel_id)
    code, body = req("GET", f"/hr/leave/{cancel_id}", tok=user)
    cd = body.get("data") or {}
    if cd.get("instanceId"):
        STATE["instances"].append(cd["instanceId"])
    code, body = req("POST", f"/hr/leave/{cancel_id}/cancel", tok=user)
    ck("本人撤销成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")
    code, body = req("GET", f"/hr/leave/{cancel_id}", tok=user)
    ck("回调：撤销后状态为 4", (body.get("data") or {}).get("leaveStatus") == 4,
       str((body.get("data") or {}).get("leaveStatus")))
    ck("回调：撤销释放占用", float(quota_of(user, 3).get("lockedDays") or 0) == 0.0,
       str(quota_of(user, 3)))

    # ================= 加班 =================
    code, body = req("POST", "/hr/request",
                     {"bizType": 1, "overtimeType": 1, "overtimeDate": "2026-10-26",
                      "hours": 8, "compensateType": 1, "reason": "自动化测试-加班转调休",
                      "startTime": "2026-10-26T19:00:00", "endTime": "2026-10-26T23:00:00"}, tok=user)
    ot_id = body.get("data")
    ck("提交加班 8 小时成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")
    if ot_id:
        STATE["requests"].append((1, ot_id))
    code, body = req("GET", "/hr/request/page?bizType=1&pageNo=1&pageSize=10", tok=admin)
    rows = ((body.get("data") or {}).get("records")) or []
    mine = [r for r in rows if r.get("id") == ot_id]
    ot_bill = mine[0].get("billCode") if mine else None
    if mine and mine[0].get("instanceId"):
        STATE["instances"].append(mine[0]["instanceId"])
    ck("加班台账可查（含单据号与姓名）", bool(mine and ot_bill and mine[0].get("realName")),
       json.dumps(mine[0], ensure_ascii=False)[:120] if mine else "")

    body = approve(admin, mine[0].get("instanceId") if mine else None)
    ck("加班审批通过", body.get("code") == 200, f"code={body.get('code')}")
    q8 = quota_of(user, 8)
    ck("回调：加班转调休 8h → 1 天", float(q8.get("totalDays") or 0) == 1.0, str(q8))

    code, body = req("POST", "/hr/request",
                     {"bizType": 1, "overtimeType": 1, "overtimeDate": "2026-10-27",
                      "hours": 20, "compensateType": 2, "reason": "自动化测试-加班 20 小时",
                      "startTime": "2026-10-27T08:00:00", "endTime": "2026-10-28T04:00:00"}, tok=user)
    if body.get("data"):
        STATE["requests"].append((1, body["data"]))
    ck("再提交 20 小时（累计 28）成功", body.get("code") == 200, f"code={body.get('code')}")

    code, body = req("POST", "/hr/request",
                     {"bizType": 1, "overtimeType": 1, "overtimeDate": "2026-10-28",
                      "hours": 20, "compensateType": 2, "reason": "自动化测试-加班超限",
                      "startTime": "2026-10-27T08:00:00", "endTime": "2026-10-28T04:00:00"}, tok=user)
    ck("单月累计超 36 小时被拒（6308）", body.get("code") == 6308,
       f"code={body.get('code')} msg={body.get('message')}")

    # ================= 出差 =================
    code, body = req("POST", "/hr/request",
                     {"bizType": 2, "destination": "上海", "startDate": "2026-10-29", "endDate": "2026-10-30",
                      "budget": 3000, "reason": "自动化测试-出差"}, tok=user)
    trip_id = body.get("data")
    ck("提交出差成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")
    if trip_id:
        STATE["requests"].append((2, trip_id))
    code, body = req("GET", "/hr/request/page?bizType=2&pageNo=1&pageSize=10", tok=admin)
    rows = ((body.get("data") or {}).get("records")) or []
    mine = [r for r in rows if r.get("id") == trip_id]
    trip_bill = mine[0].get("billCode") if mine else None
    if mine and mine[0].get("instanceId"):
        STATE["instances"].append(mine[0]["instanceId"])
    ck("出差天数按起止计算（2 天）", bool(mine and float(mine[0].get("days") or 0) == 2.0),
       str(mine[0].get("days") if mine else None))

    body = approve(admin, mine[0].get("instanceId") if mine else None)
    ck("出差审批通过", body.get("code") == 200, f"code={body.get('code')}")

    # 出差与请假冲突：先请假 11-02 再请同区间出差
    code, body = req("POST", "/hr/leave",
                     {"leaveType": 1, "startTime": "2026-11-02T09:00:00", "endTime": "2026-11-02T18:00:00",
                      "leaveDays": 1, "reason": "自动化测试-与出差冲突"}, tok=user)
    if body.get("data"):
        STATE["leaves"].append(body["data"])
    code, body = req("GET", f"/hr/leave/{STATE['leaves'][-1]}", tok=user)
    if (body.get("data") or {}).get("instanceId"):
        STATE["instances"].append(body["data"]["instanceId"])
    code, body = req("POST", "/hr/request",
                     {"bizType": 2, "destination": "北京", "startDate": "2026-11-02", "endDate": "2026-11-03",
                      "budget": 1000, "reason": "自动化测试-冲突"}, tok=user)
    ck("出差与请假冲突被拒（6309）", body.get("code") == 6309, f"code={body.get('code')} msg={body.get('message')}")

    # ================= 补卡 =================
    code, body = req("POST", "/hr/request",
                     {"bizType": 3, "cardDate": "2026-10-22", "direction": 1,
                      "cardTime": "2026-10-22T09:00:00", "reason": "自动化测试-补卡"}, tok=user)
    card_id = body.get("data")
    ck("提交补卡成功", body.get("code") == 200, f"code={body.get('code')} msg={body.get('message')}")
    if card_id:
        STATE["requests"].append((3, card_id))

    code, body = req("POST", "/hr/request",
                     {"bizType": 3, "cardDate": "2026-10-22", "direction": 1,
                      "cardTime": "2026-10-22T09:10:00", "reason": "自动化测试-重复补卡"}, tok=user)
    ck("同日同方向重复补卡被拒（6310）", body.get("code") == 6310, f"code={body.get('code')}")

    code, body = req("POST", "/hr/request",
                     {"bizType": 3, "cardDate": "2026-08-01", "direction": 2,
                      "cardTime": "2026-08-01T18:00:00", "reason": "自动化测试-超期补卡"}, tok=user)
    ck("超过 30 天的补卡被拒（6202）", body.get("code") == 6202, f"code={body.get('code')}")

    code, body = req("GET", "/hr/request/page?bizType=3&pageNo=1&pageSize=10", tok=admin)
    rows = ((body.get("data") or {}).get("records")) or []
    mine = [r for r in rows if r.get("id") == card_id]
    if mine and mine[0].get("instanceId"):
        STATE["instances"].append(mine[0]["instanceId"])
    body = approve(admin, mine[0].get("instanceId") if mine else None)
    ck("补卡审批通过", body.get("code") == 200, f"code={body.get('code')}")

    cleanup(admin, user)
    return summary()


def cleanup(admin, user):
    """删除测试数据：业务行 + 审批实例 + Flowable 运行/历史。"""
    for leave_id in STATE["leaves"]:
        mysql(f"DELETE FROM oa_system.oa_leave WHERE id={leave_id}")
    for biz, rid in STATE["requests"]:
        table = {1: "oa_overtime", 2: "oa_business_trip", 3: "oa_card_supplement"}[biz]
        mysql(f"DELETE FROM oa_system.{table} WHERE id={rid}")
    for inst in {i for i in STATE["instances"] if i}:
        proc = mysql(f"SELECT IFNULL(proc_inst_id,'') FROM oa_system.oa_approval_instance WHERE id={inst}").strip()
        mysql(f"DELETE FROM oa_system.oa_approval_record WHERE instance_id={inst}")
        mysql(f"DELETE FROM oa_system.oa_approval_cc WHERE instance_id={inst}")
        mysql(f"DELETE FROM oa_system.oa_approval_instance WHERE id={inst}")
        if proc:
            for table in ("act_ru_task", "act_ru_execution", "act_ru_identitylink", "act_ru_variable",
                          "act_ru_actinst", "act_hi_taskinst", "act_hi_actinst", "act_hi_procinst",
                          "act_hi_varinst", "act_hi_identitylink", "act_hi_comment"):
                mysql(f"DELETE FROM oa_system.{table} WHERE PROC_INST_ID_='{proc}'")
            mysql(f"DELETE FROM oa_system.act_hi_tsk_log WHERE PROC_INST_ID_='{proc}'")
    # 额度复原：删掉测试期间产生的额度行（下次访问会自动按工龄重建）
    mysql(f"DELETE FROM oa_system.oa_leave_quota WHERE user_id={USER_ID} AND year={YEAR}")
    print("cleanup:", mysql(
        f"SELECT CONCAT('leave=',(SELECT COUNT(*) FROM oa_system.oa_leave WHERE user_id={USER_ID}),"
        f"' overtime=',(SELECT COUNT(*) FROM oa_system.oa_overtime WHERE user_id={USER_ID}),"
        f"' trip=',(SELECT COUNT(*) FROM oa_system.oa_business_trip WHERE user_id={USER_ID}),"
        f"' card=',(SELECT COUNT(*) FROM oa_system.oa_card_supplement WHERE user_id={USER_ID}),"
        f"' quota=',(SELECT COUNT(*) FROM oa_system.oa_leave_quota WHERE user_id={USER_ID}))"))


if __name__ == "__main__":
    sys.exit(main())
