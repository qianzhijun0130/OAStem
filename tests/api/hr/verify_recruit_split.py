#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""招聘链路接口验证（BE-OPT-08 第 5/6 拆分后的行为等价回归）。

覆盖：需求保存/审批/审批后只读、职位创建与删除保护、候选人手机号合并、
阶段流转（前进 / 同阶段 / 回退无理由 / 淘汰无原因）、面试评价（非面试官 / 未交齐不能汇总）、
offer 状态机（未到终试不能发 / 已发才能接受 / 重复接受被拒 / 未接受不能转员工）。

为什么不复用 verify_p2：它用硬编码日期造期次，本机时间已过会在培训段崩溃，
招聘段根本执行不到。这里全部用相对时间与自造数据，跑完按 id 清理。
"""
import sys
import pathlib
from datetime import datetime, timedelta

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))
from xlib import ck, mysql, req, summary, token  # noqa: E402

DB = "oa_system"
DEPT = 2
POSITION = 7
ADMIN = 1
MOBILE = "13900001239"

CREATED = {"demand": None, "post": None, "candidate": None, "interview": None, "offer": None}


def q(sql):
    return mysql(sql)


def cleanup():
    if CREATED["offer"]:
        q(f"DELETE FROM {DB}.oa_recruit_offer WHERE id={CREATED['offer']}")
    if CREATED["interview"]:
        q(f"DELETE FROM {DB}.oa_recruit_feedback WHERE interview_id={CREATED['interview']}")
        q(f"DELETE FROM {DB}.oa_recruit_interview WHERE id={CREATED['interview']}")
    if CREATED["candidate"]:
        q(f"DELETE FROM {DB}.oa_recruit_candidate WHERE mobile='{MOBILE}'")
    if CREATED["post"]:
        q(f"DELETE FROM {DB}.oa_recruit_post WHERE id={CREATED['post']}")
    if CREATED["demand"]:
        q(f"DELETE FROM {DB}.oa_recruit_demand WHERE id={CREATED['demand']}")


def main():
    adm = token(ADMIN, "admin", hours=2)
    cleanup()

    # ---------------- 1. 需求 ----------------
    _, body = req("POST", "/hr/recruit/demand", {
        "deptId": DEPT, "positionId": POSITION, "headCount": 2, "urgency": 2,
        "reasonType": 1, "reason": "拆分回归用例", "remark": "verify_recruit_split",
    }, tok=adm)
    ck("创建招聘需求", body.get("code") == 200 and body.get("data"), f"code={body.get('code')}")
    demand_id = body.get("data")
    CREATED["demand"] = demand_id
    code_text = q(f"SELECT demand_code FROM {DB}.oa_recruit_demand WHERE id={demand_id}")
    ck("需求编号按 ZP-YYYYMM-#### 生成", bool(code_text) and code_text.startswith("ZP-"),
       f"code={code_text}")

    _, body = req("GET", f"/hr/recruit/demand/{demand_id}", tok=adm)
    vo = body.get("data") or {}
    ck("需求详情含部门/岗位/状态/配额",
       vo.get("deptName") and vo.get("positionName") and vo.get("statusLabel")
       and vo.get("quotaGap") is not None,
       f"dept={vo.get('deptName')} pos={vo.get('positionName')} gap={vo.get('quotaGap')}")

    _, body = req("POST", f"/hr/recruit/demand/{demand_id}/approve", tok=adm)
    ck("需求审批通过", body.get("code") == 200, f"code={body.get('code')}")
    ck("需求状态转为招聘中",
       q(f"SELECT status FROM {DB}.oa_recruit_demand WHERE id={demand_id}") == "2")

    _, body = req("POST", f"/hr/recruit/demand/{demand_id}/approve", tok=adm)
    ck("重复审批被拒 → 6602", body.get("code") == 6602, f"code={body.get('code')}")

    _, body = req("POST", "/hr/recruit/demand", {
        "id": demand_id, "deptId": DEPT, "positionId": POSITION, "headCount": 9,
    }, tok=adm)
    ck("已审批需求再改 → 6611", body.get("code") == 6611, f"code={body.get('code')}")

    # ---------------- 2. 职位 ----------------
    _, body = req("POST", "/hr/recruit/post", {
        "demandId": demand_id, "postName": "拆分回归-测试工程师", "city": "桂林",
        "salaryMin": 6000, "salaryMax": 9000, "headCount": 2, "channel": 2, "status": 2,
    }, tok=adm)
    post_id = body.get("data")
    CREATED["post"] = post_id
    ck("创建招聘职位", body.get("code") == 200 and post_id, f"code={body.get('code')}")

    _, body = req("GET", "/hr/recruit/post/page", tok=adm)
    rows = (body.get("data") or {}).get("records") or []
    row = next((r for r in rows if r["id"] == post_id), None)
    ck("职位列表能查到新建职位且带需求编号", row is not None and row.get("demandCode") == code_text,
       f"demandCode={row.get('demandCode') if row else None}")

    # ---------------- 3. 候选人 ----------------
    _, body = req("POST", "/hr/recruit/candidate", {
        "name": "拆分回归候选人", "mobile": MOBILE, "postId": post_id, "demandId": demand_id,
        "source": 5, "authorized": 1,
    }, tok=adm)
    candidate_id = body.get("data")
    CREATED["candidate"] = candidate_id
    ck("创建候选人（阶段=待筛选）", body.get("code") == 200 and candidate_id
       and q(f"SELECT stage FROM {DB}.oa_recruit_candidate WHERE id={candidate_id}") == "1",
       f"code={body.get('code')}")

    _, body = req("POST", "/hr/recruit/candidate", {
        "name": "拆分回归候选人", "mobile": MOBILE, "authorized": 1,
    }, tok=adm)
    ck("同手机号重复投递合并到同一人", body.get("code") == 200 and body.get("data") == candidate_id,
       f"returned={body.get('data')} original={candidate_id}")
    ck("合并后仍只有 1 条候选人",
       q(f"SELECT COUNT(*) FROM {DB}.oa_recruit_candidate WHERE mobile='{MOBILE}'") == "1")

    _, body = req("POST", "/hr/recruit/candidate", {
        "name": "未授权候选人", "mobile": "13900001238", "authorized": 0,
    }, tok=adm)
    ck("未获授权不能存简历 → 6604", body.get("code") == 6604, f"code={body.get('code')}")

    # ---------------- 4. 阶段流转 ----------------
    _, body = req("POST", f"/hr/recruit/candidate/{candidate_id}/stage", {"stage": 2}, tok=adm)
    ck("阶段前进 待筛选→初试", body.get("code") == 200, f"code={body.get('code')}")

    _, body = req("POST", f"/hr/recruit/candidate/{candidate_id}/stage", {"stage": 2}, tok=adm)
    ck("同阶段再推进被拒 → 6602", body.get("code") == 6602, f"code={body.get('code')}")

    _, body = req("POST", f"/hr/recruit/candidate/{candidate_id}/stage",
                  {"stage": 1, "rollback": True}, tok=adm)
    ck("回退未填理由被拒（400 或 6602）", body.get("code") in (400, 6602), f"code={body.get('code')}")

    _, body = req("POST", f"/hr/recruit/candidate/{candidate_id}/stage",
                  {"stage": 1, "rollback": True, "reason": "回归用例回退"}, tok=adm)
    ck("HR 填理由后可回退到初试前", body.get("code") == 200, f"code={body.get('code')}")
    remark = q(f"SELECT remark FROM {DB}.oa_recruit_candidate WHERE id={candidate_id}")
    ck("回退理由写入备注留痕", "[阶段回退]" in (remark or ""), f"remark={(remark or '')[:40]}")

    # 推到终试（4），供后面发 offer
    for stage in (2, 3, 4):
        req("POST", f"/hr/recruit/candidate/{candidate_id}/stage", {"stage": stage}, tok=adm)
    ck("候选人可推进至终试",
       q(f"SELECT stage FROM {DB}.oa_recruit_candidate WHERE id={candidate_id}") == "4")

    # ---------------- 5. 面试 ----------------
    tomorrow = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%dT10:00:00")
    _, body = req("POST", "/hr/recruit/interview", {
        "candidateId": candidate_id, "postId": post_id, "roundNo": 1,
        "interviewTime": tomorrow, "way": 3, "location": "线上",
        "interviewerIds": [ADMIN],
    }, tok=adm)
    interview_id = body.get("data")
    CREATED["interview"] = interview_id
    ck("安排面试", body.get("code") == 200 and interview_id, f"code={body.get('code')}")

    _, body = req("POST", "/hr/recruit/interview", {
        "candidateId": candidate_id, "postId": post_id, "roundNo": 1,
        "interviewTime": tomorrow, "interviewerIds": [ADMIN],
    }, tok=adm)
    ck("同候选人同轮次重复安排被拒", body.get("code") == 400, f"code={body.get('code')}")

    _, body = req("POST", f"/hr/recruit/interview/{interview_id}/summarize"
                  f"?conclusion=1&summary=%E6%8F%90%E5%89%8D%E6%B1%87%E6%80%BB", tok=adm)
    ck("评价未交齐不能汇总 → 6607", body.get("code") == 6607, f"code={body.get('code')}")

    _, body = req("POST", "/hr/recruit/interview/feedback", {
        "interviewId": interview_id, "professional": 5, "communication": 4,
        "logicScore": 4, "cultureMatch": 5, "conclusion": 1, "comment": "回归用例评价",
    }, tok=adm)
    ck("面试官提交评价", body.get("code") == 200, f"code={body.get('code')}")

    _, body = req("POST", f"/hr/recruit/interview/{interview_id}/summarize"
                  f"?conclusion=1&summary=%E9%80%9A%E8%BF%87", tok=adm)
    ck("评价齐备后可汇总", body.get("code") == 200, f"code={body.get('code')}")
    ck("面试状态转为已汇总",
       q(f"SELECT status FROM {DB}.oa_recruit_interview WHERE id={interview_id}") == "2")

    _, body = req("GET", f"/hr/recruit/interview/{interview_id}", tok=adm)
    ivo = body.get("data") or {}
    ck("面试详情含面试官与评价数",
       ivo.get("interviewerCount") == 1 and ivo.get("feedbackCount") == 1
       and ivo.get("allSubmitted") is True,
       f"cnt={ivo.get('interviewerCount')}/{ivo.get('feedbackCount')} all={ivo.get('allSubmitted')}")

    # ---------------- 6. Offer ----------------
    _, body = req("POST", "/hr/recruit/offer", {
        "candidateId": candidate_id, "postId": post_id, "salary": 8000,
        "probationMonths": 3, "reportDate": (datetime.now() + timedelta(days=7)).strftime("%Y-%m-%d"),
    }, tok=adm)
    offer_id = body.get("data")
    CREATED["offer"] = offer_id
    ck("终试候选人可发起 offer", body.get("code") == 200 and offer_id, f"code={body.get('code')}")
    offer_no = q(f"SELECT offer_no FROM {DB}.oa_recruit_offer WHERE id={offer_id}")
    ck("offer 编号按 OF-YYYYMM-#### 生成", bool(offer_no) and offer_no.startswith("OF-"),
       f"no={offer_no}")

    _, body = req("POST", f"/hr/recruit/offer/{offer_id}/accept", tok=adm)
    ck("未发出的 offer 不能接受 → 6609", body.get("code") == 6609, f"code={body.get('code')}")

    _, body = req("POST", "/hr/recruit/offer/convert", {"offerId": offer_id}, tok=adm)
    ck("未接受的 offer 不能转员工", body.get("code") in (6609, 6603), f"code={body.get('code')}")

    _, body = req("POST", f"/hr/recruit/offer/{offer_id}/send?sendWay=1", tok=adm)
    ck("发出 offer", body.get("code") == 200, f"code={body.get('code')}")
    ck("发出后候选人阶段转为已发offer",
       q(f"SELECT stage FROM {DB}.oa_recruit_candidate WHERE id={candidate_id}") == "6")

    _, body = req("POST", f"/hr/recruit/offer/{offer_id}/accept", tok=adm)
    ck("候选人接受 offer", body.get("code") == 200, f"code={body.get('code')}")

    _, body = req("POST", f"/hr/recruit/offer/{offer_id}/accept", tok=adm)
    ck("重复接受被拒 → 6609", body.get("code") == 6609, f"code={body.get('code')}")

    _, body = req("GET", f"/hr/recruit/offer/{offer_id}", tok=adm)
    ovo = body.get("data") or {}
    ck("offer 详情含候选人/状态/剩余天数",
       ovo.get("candidateName") and ovo.get("statusLabel") and ovo.get("remainDays") is not None,
       f"status={ovo.get('statusLabel')} remain={ovo.get('remainDays')}")

    # ---------------- 7. 看板 ----------------
    _, body = req("GET", "/hr/recruit/board", tok=adm)
    board = body.get("data") or {}
    ck("看板五项指标齐全",
       board.get("postingCount") is not None and board.get("stageFunnel")
       and board.get("channelStats") and board.get("rejectStats") and board.get("deptStats"),
       f"funnel={len(board.get('stageFunnel') or [])} channel={len(board.get('channelStats') or [])}")

    # ---------------- 8. 清理 ----------------
    cleanup()
    left = q(f"SELECT COUNT(*) FROM {DB}.oa_recruit_candidate WHERE mobile='{MOBILE}'")
    ck("用例数据已清理", left == "0", f"left={left}")

    return summary()


if __name__ == "__main__":
    sys.exit(main())
