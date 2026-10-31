#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P1-1 敏感字段加密验证：写入落密文、读取自解密、掩码分级、迁移幂等、历史明文兼容。"""
import sys

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "lib"))
import xlib as X

DB = X.DB
ADMIN_ID, ADMIN_NAME = 1, "admin"
STAFF_ID, STAFF_NAME = 101809, "tech_staff1"      # 普通员工（被操作对象）
OTHER_ID, OTHER_NAME = 101810, "tech_staff2"      # 无关员工（越权校验）

# 必须用「校验码正确」的号码：BIZ-OPT-09 起档案保存会校验校验码，
# 旧值 …1234 的校验码应为 3，用它会让「HR 更新档案成功」直接 400。
ID_CARD = "110101199003071233"
BANK = "6222021234567890123"
SOCIAL = "SH12345678"
FUND = "GJJ87654321"

adm = X.token(ADMIN_ID, ADMIN_NAME)
staff = X.token(STAFF_ID, STAFF_NAME)
other = X.token(OTHER_ID, OTHER_NAME)

print("== 0. 清理上一轮残留 ==")
X.mysql(f"UPDATE {DB}.oa_user SET id_card=NULL WHERE id IN ({STAFF_ID},{OTHER_ID})")
X.mysql(f"DELETE FROM {DB}.oa_employee_bank WHERE user_id={STAFF_ID}")

print("== 1. HR 写入敏感字段 ==")
st, body = X.req("POST", f"/hr/employee/{STAFF_ID}/profile", body={
    "idCard": ID_CARD, "bankName": "工商银行",
    "bankAccount": BANK, "socialNo": SOCIAL, "fundNo": FUND,
}, tok=adm)
X.ck("HR 更新档案成功", st == 200 and body.get("code") == 200, f"st={st} code={body.get('code')} msg={body.get('message')}")

print("== 2. 库中必须是密文 ==")
raw = X.mysql(f"SELECT id_card FROM {DB}.oa_user WHERE id={STAFF_ID}")
X.ck("oa_user.id_card 落库为密文", raw.startswith("ENC:"), f"raw={raw[:40]}...")
X.ck("oa_user.id_card 不含明文", ID_CARD not in raw)

raw_bank = X.mysql(f"SELECT bank_account FROM {DB}.oa_employee_bank WHERE user_id={STAFF_ID}")
X.ck("bank_account 落库为密文", raw_bank.startswith("ENC:"), f"raw={raw_bank[:40]}...")
raw_social = X.mysql(f"SELECT social_no FROM {DB}.oa_employee_bank WHERE user_id={STAFF_ID}")
X.ck("social_no 落库为密文", raw_social.startswith("ENC:"))

print("== 3. HR 读取解密为原文 ==")
st, body = X.req("GET", f"/hr/employee/{STAFF_ID}", tok=adm)
d = body.get("data") or {}
X.ck("HR 可看原文 canViewSensitive", d.get("canViewSensitive") is True)
X.ck("idCard 解密还原", d.get("idCard") == ID_CARD, f"got={d.get('idCard')}")
X.ck("bankAccount 解密还原", d.get("bankAccount") == BANK, f"got={d.get('bankAccount')}")
X.ck("socialNo 解密还原", d.get("socialNo") == SOCIAL, f"got={d.get('socialNo')}")
X.ck("fundNo 解密还原", d.get("fundNo") == FUND, f"got={d.get('fundNo')}")

print("== 4. 本人读取为掩码 ==")
st, body = X.req("GET", f"/hr/employee/{STAFF_ID}", tok=staff)
d = body.get("data") or {}
X.ck("本人不可看敏感原文", d.get("canViewSensitive") is False)
# 期望值由 ID_CARD 推导，避免改号码时还要同步改字面量
X.ck("idCard 已掩码", d.get("idCard") == ID_CARD[:3] + "*" * 11 + ID_CARD[-4:],
     f"got={d.get('idCard')}")
X.ck("bankAccount 已掩码", d.get("bankAccount") == "**** **** **** 0123", f"got={d.get('bankAccount')}")

print("== 5. 越权：他人档案 403 ==")
st, body = X.req("GET", f"/hr/employee/{STAFF_ID}", tok=other)
X.ck("无关员工访问他人档案被拒", body.get("code") == 403, f"code={body.get('code')}")

print("== 6. 重复写入不套娃加密（幂等） ==")
before = X.mysql(f"SELECT id_card FROM {DB}.oa_user WHERE id={STAFF_ID}")
X.req("POST", f"/hr/employee/{STAFF_ID}/profile", body={"idCard": ID_CARD}, tok=adm)
st, body = X.req("GET", f"/hr/employee/{STAFF_ID}", tok=adm)
X.ck("二次写入后仍能还原原文", (body.get('data') or {}).get("idCard") == ID_CARD,
     f"got={(body.get('data') or {}).get('idCard')}")
after = X.mysql(f"SELECT id_card FROM {DB}.oa_user WHERE id={STAFF_ID}")
X.ck("二次写入产生新随机 IV（密文变化但可解）", after != before and after.startswith("ENC:"))

print("== 7. 历史明文兼容读取（迁移前不 500） ==")
X.mysql(f"UPDATE {DB}.oa_user SET id_card='{ID_CARD}' WHERE id={OTHER_ID}")
st, body = X.req("GET", f"/hr/employee/{OTHER_ID}", tok=adm)
X.ck("库中明文仍可正常读取", st == 200 and (body.get('data') or {}).get("idCard") == ID_CARD,
     f"st={st} got={(body.get('data') or {}).get('idCard')}")
plain = X.mysql(f"SELECT id_card FROM {DB}.oa_user WHERE id={OTHER_ID}")
X.ck("明文未被读取动作改写", plain == ID_CARD)

print("== 8. 空值不报错 ==")
st, body = X.req("POST", f"/hr/employee/{OTHER_ID}/profile", body={"idCard": ""}, tok=adm)
X.ck("清空身份证不报错", st == 200 and body.get("code") == 200, f"st={st} code={body.get('code')}")

sys.exit(X.summary())
