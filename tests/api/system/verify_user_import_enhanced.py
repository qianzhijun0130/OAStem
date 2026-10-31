"""
BIZ-OPT-09 批量入职导入增强与建档数据治理 —— 接口级验证。

覆盖方案里的四条验收标准：
  ① 新模板导入成功、试用期截止日自动计算、任职状态为「试用」而非「正式」；
  ② 身份证重复行失败并返回已存在档案的工号，其余行成功；
  ③ 试用期超出合同期限上限的行被拒绝；
  ④ 完整度看板显示各缺口的数量，清单可翻页、可导出。

另外覆盖本轮实现时补上的几条约束：
  - 旧 10 列模板给出「请下载新模板」而不是「第 11 列表头应为…」；
  - 无固定期限合同不得填结束日期；固定期限必须填；
  - 直属上级工号不存在 → 该行失败；
  - 待入职（入职日在未来）的账号默认不可登录（status=0）；
  - 部分成功：坏行跳过，好行照常入库，且返回码仍是 200。

⚠️ 数据全部自建、跑完物理删除（`IMP-` 前缀的账号 + `id_card_hash` 命中的行），
不依赖库里既有员工——上一次因为依赖既有周期被别的用例删掉而「单跑绿、批跑红」。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, ck, mysql, mysql_exec, req, summary, token, upload  # noqa: E402

from openpyxl import Workbook  # noqa: E402

# ---------------------------------------------------------------- 常量

HEADERS = ["登录账号", "姓名", "手机号", "部门", "岗位", "邮箱", "性别", "入职日期", "工号", "角色",
           "身份证号", "合同类型", "合同开始日期", "合同结束日期", "试用期月数",
           "参加工作日期", "直属上级工号", "任职状态"]

USER_PREFIX = "IMPV_"
MOBILE_PREFIX = "13800020"

# 合法身份证：前 17 位 + 校验码（与后端 IdCardUtil 同一算法）
_W = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
_C = "10X98765432"


def id_card(seq: int) -> str:
    """生成一个校验码合法的 18 位身份证（前 17 位固定，仅末两位变化保证唯一）"""
    prefix = f"11010119900307{seq:03d}"[:17]
    return prefix + _C[sum(int(prefix[i]) * _W[i] for i in range(17)) % 11]


def xlsx(rows):
    wb = Workbook()
    ws = wb.active
    ws.title = "员工数据"
    ws.append(HEADERS)
    for row in rows:
        ws.append(row)
    import io
    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()


def legacy_xlsx(rows):
    """旧版 10 列模板"""
    wb = Workbook()
    ws = wb.active
    ws.append(HEADERS[:10])
    for row in rows:
        ws.append(row[:10])
    import io
    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()


def row(seq: int, username: str, real_name: str, **kw):
    """按列顺序拼一行，未指定的留空"""
    base = [username, real_name, f"{MOBILE_PREFIX}{seq:03d}", "技术部", "工程师", "", "男",
            "2026-11-01", "", "普通员工", id_card(seq), "固定期限", "2026-11-01", "2027-11-01",
            "", "", "", ""]
    idx = {"idCard": 10, "contractType": 11, "start": 12, "end": 13, "probation": 14,
           "workStart": 15, "leader": 16, "status": 17, "entry": 7, "workNumber": 8}
    for key, value in kw.items():
        base[idx[key]] = value
    return base


def cleanup():
    """物理删除本用例造的数据（按账号前缀 + 合同编号前缀定位）"""
    ids = mysql(f"SELECT GROUP_CONCAT(id) FROM {DB}.oa_user WHERE username LIKE '{USER_PREFIX}%'")
    if ids:
        mysql_exec(f"DELETE FROM {DB}.oa_employee_contract WHERE user_id IN ({ids})")
        mysql_exec(f"DELETE FROM {DB}.oa_user_role WHERE user_id IN ({ids})")
        mysql_exec(f"DELETE FROM {DB}.oa_user WHERE id IN ({ids})")


def user_of(username):
    return mysql(
        f"SELECT u.id, u.employment_status, u.status, u.work_start_date, u.probation_end_date, "
        f"u.direct_leader_id, u.id_card_hash FROM {DB}.oa_user u "
        f"WHERE u.username='{username}' LIMIT 1")


def contract_of(username):
    return mysql(
        f"SELECT c.contract_no, c.contract_type, c.start_date, c.end_date, c.sign_count, c.status "
        f"FROM {DB}.oa_employee_contract c JOIN {DB}.oa_user u ON u.id=c.user_id "
        f"WHERE u.username='{username}' LIMIT 1")


def main():
    admin = token(1, "admin")
    cleanup()

    # ---------------------------------------------------------------- 1. 模板
    print("== 1. 模板表头与旧模板识别 ==")
    # 模板下载是二进制响应，必须 raw=True，否则 json.loads 会抛异常
    status, blob = req("GET", "/system/user/import-template", tok=admin, raw=True)
    ck("模板下载成功且是 xlsx（PK 头）", status == 200 and blob[:2] == b"PK",
       f"status={status} bytes={len(blob) if blob else 0}")

    _, body = upload("/system/user/import", "legacy.xlsx",
                     legacy_xlsx([["IMPV_LEGACY", "旧模板", "13800002999", "技术部", "工程师",
                                   "", "男", "2026-11-01", "", "普通员工"]]), tok=admin)
    msg = body.get("message") or ""
    ck("旧 10 列模板被拒（1111）", body.get("code") == 1111, f"code={body.get('code')}")
    ck("提示语明确要求下载新模板", "旧版" in msg and "下载" in msg, msg)

    # ---------------------------------------------------------------- 2. 正常导入
    print("== 2. 完整导入：任职状态 / 试用期 / 参加工作日期 / 上级 / 合同 ==")
    _, body = upload("/system/user/import", "ok.xlsx", xlsx([
        # 入职日在未来 → 待入职；1 年期合同 → 试用期上限 2 个月
        row(1, "IMPV_A", "导入甲", entry="2027-01-01", start="2027-01-01", end="2028-01-01",
            probation="1", workStart="2020-07-01"),
        # 入职日已过 → 试用；无固定期限 → 上限 6 个月
        row(2, "IMPV_B", "导入乙", entry="2026-11-01", contractType="无固定期限",
            start="2026-11-01", end="", probation="3", workStart="2018-03-01",
            leader="A001", status="试用"),
    ]), tok=admin)
    data = body.get("data") or {}
    ck("两行全部导入成功", data.get("successCount") == 2 and data.get("failCount") == 0,
       f"success={data.get('successCount')} fail={data.get('failCount')} "
       f"errors={data.get('errors')}")

    a = user_of("IMPV_A")
    ck("未来入职日 → 任职状态=待入职(1)", a and a.split("\t")[1] == "1", f"row={a}")
    ck("待入职账号不可登录（status=0）", a and a.split("\t")[2] == "0", f"row={a}")
    # 2027-01-01 + 1 个月 - 1 天 = 2027-01-31
    ck("试用期截止日 = 入职日+月数-1 天", a and a.split("\t")[4] == "2027-01-31",
       f"probationEnd={(a or '').split(chr(9))[4] if a else None}")
    ck("参加工作日期落库", a and a.split("\t")[3] == "2020-07-01", f"row={a}")
    ck("身份证盲索引已写入", a and (a.split("\t")[6] or "") != "", f"row={a}")

    b = user_of("IMPV_B")
    ck("已过入职日 → 任职状态=试用(2)", b and b.split("\t")[1] == "2", f"row={b}")
    ck("试用员工账号可登录（status=1）", b and b.split("\t")[2] == "1", f"row={b}")
    ck("直属上级工号解析成功", b and (b.split("\t")[5] or "") != "", f"row={b}")

    ca = contract_of("IMPV_A")
    ck("合同已建档（固定期限）", ca and ca.split("\t")[1] == "1", f"contract={ca}")
    ck("合同编号含真实工号（HT-工号-日期）",
       ca and ca.split("\t")[0].startswith("HT-") and "-20270101" in ca.split("\t")[0],
       f"contractNo={(ca or '').split(chr(9))[0] if ca else None}")
    ck("合同状态=生效(1)、签订次数=1",
       ca and ca.split("\t")[4] == "1" and ca.split("\t")[5] == "1", f"contract={ca}")

    cb = contract_of("IMPV_B")
    # mysql 客户端把 SQL NULL 打成字符串 "NULL"，不能直接和 "" 比
    end_of_b = cb.split("\t")[3] if cb else None
    ck("无固定期限合同结束日为空", end_of_b in ("", "NULL"), f"end={end_of_b}")

    # ---------------------------------------------------------------- 3. 部分成功 + 身份证重复
    print("== 3. 身份证重复：坏行跳过、好行照常入库 ==")
    dup_card = id_card(1)  # 与 IMP-V-A 相同
    _, body = upload("/system/user/import", "dup.xlsx", xlsx([
        row(3, "IMPV_C", "导入丙", idCard=dup_card),
        row(4, "IMPV_D", "导入丁"),
    ]), tok=admin)
    data = body.get("data") or {}
    ck("接口仍返回 200（坏行不阻断）", body.get("code") == 200, f"code={body.get('code')}")
    ck("1 成功 1 失败", data.get("successCount") == 1 and data.get("failCount") == 1,
       f"success={data.get('successCount')} fail={data.get('failCount')}")
    err = (data.get("errors") or [{}])[0]
    ck("失败行号正确（第 2 行）", err.get("row") == 2, f"row={err.get('row')}")
    ck("提示包含已存在档案的工号", "工号" in (err.get("message") or ""),
       err.get("message"))
    ck("重复行未落库", user_of("IMPV_C") == "", f"row={user_of('IMP-V-C')}")
    ck("正常行已落库", user_of("IMPV_D") != "", "")

    # ---------------------------------------------------------------- 4. 试用期超上限
    print("== 4. 试用期超出法定上限 ==")
    _, body = upload("/system/user/import", "probation.xlsx", xlsx([
        # 1 年期合同（12 个月）→ 上限 2 个月，填 6 个月必须被拒
        row(5, "IMPV_E", "导入戊", probation="6", start="2026-11-01", end="2027-11-01"),
    ]), tok=admin)
    data = body.get("data") or {}
    msg = ((data.get("errors") or [{}])[0]).get("message") or ""
    ck("试用期超上限被拒", data.get("successCount") == 0 and "法定上限" in msg, msg)
    ck("提示给出该合同期限的上限值（2 个月）", "最长 2 个月" in msg, msg)

    # ---------------------------------------------------------------- 5. 其它行级规则
    print("== 5. 上级不存在 / 合同日期不自洽 ==")
    _, body = upload("/system/user/import", "rules.xlsx", xlsx([
        row(6, "IMPV_F", "导入己", leader="NOPE9999"),
        row(7, "IMPV_G", "导入庚", contractType="无固定期限", start="2026-11-01", end="2027-11-01"),
        row(8, "IMPV_H", "导入辛", start="2026-11-01", end="2026-01-01"),
    ]), tok=admin)
    data = body.get("data") or {}
    messages = {e["row"]: e["message"] for e in (data.get("errors") or [])}
    ck("三行全部失败", data.get("successCount") == 0 and data.get("failCount") == 3,
       f"errors={messages}")
    ck("上级工号不存在被拦下", "不存在" in (messages.get(2) or ""), messages.get(2) or "")
    ck("无固定期限不得填结束日期", "无固定期限" in (messages.get(3) or ""), messages.get(3) or "")
    ck("结束日必须晚于开始日", "晚于" in (messages.get(4) or ""), messages.get(4) or "")

    # ---------------------------------------------------------------- 6. 完整度看板
    print("== 6. 建档完整度看板 ==")
    _, body = req("GET", "/hr/employee/completeness/summary", tok=admin)
    data = body.get("data") or {}
    cards = {c["dimension"]: c for c in (data.get("cards") or [])}
    ck("六个维度齐全",
       set(cards) == {"noIdCard", "noContract", "noWorkStartDate", "noLeader",
                      "noSalaryProfile", "noBankAccount"}, f"dims={sorted(cards)}")
    ck("在职总人数 > 0", (data.get("totalOnDuty") or 0) > 0, f"total={data.get('totalOnDuty')}")
    ck("维度计数为数字且不超过总人数",
       all(0 <= c["count"] <= data.get("totalOnDuty", 0) for c in cards.values()),
       f"cards={ {k: v['count'] for k, v in cards.items()} }")
    ck("占比为 0~100 之间", all(0 <= c["percent"] <= 100 for c in cards.values()), "")

    _, body = req("GET", "/hr/employee/completeness/items?dimension=noContract&pageNo=1&pageSize=5",
                  tok=admin)
    page = body.get("data") or {}
    ck("清单可分页", body.get("code") == 200 and len(page.get("records") or []) <= 5,
       f"rows={len(page.get('records') or [])}")
    ck("清单条数与卡片计数一致", page.get("total") == cards["noContract"]["count"],
       f"total={page.get('total')} card={cards['noContract']['count']}")
    ck("清单行含工号与部门",
       all("workNumber" in r and "deptName" in r for r in (page.get("records") or [])), "")

    _, body = req("GET", "/hr/employee/completeness/items?dimension=unknownDim", tok=admin)
    ck("未知维度返回 400", body.get("code") == 400, f"code={body.get('code')}")

    # ---------------------------------------------------------------- 7. 清理
    print("== 7. 清理 ==")
    cleanup()
    left = mysql(f"SELECT COUNT(*) FROM {DB}.oa_user WHERE username LIKE '{USER_PREFIX}%'")
    ck("用例数据已清理", left == "0", f"left={left}")

    return summary()


if __name__ == "__main__":
    sys.exit(main())
