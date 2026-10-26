#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发起流程：选项图标 / 草稿保存-更新-提交 / 单据编号 验证。"""
import base64, hashlib, hmac, json, os, re, subprocess, sys, time, urllib.error, urllib.request
from pathlib import Path

BASE="http://127.0.0.1:8080"
PROPS=[Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application.properties"),
       Path(r"F:/GitHub/OAStem/oa-system/src/main/resources/application-local.properties")]
MYSQL=r"D:/Program Files/mysql-8.0.46-winx64/bin/mysql.exe"; DB="oa_system"
R=[]
def lp():
    d={}
    for p in PROPS:
        if p.exists():
            for l in p.read_text(encoding="utf-8").splitlines():
                l=l.strip()
                if l and not l.startswith("#") and "=" in l:
                    k,v=l.split("=",1); d[k.strip()]=v.strip()
    return d
PD=lp(); SEC=PD["jwt.secret"].encode()
U=PD.get("spring.datasource.username","root"); U=U.split(":")[-1].rstrip("}") if U.startswith("${") else U
PW=PD.get("spring.datasource.password","")
def mysql(sql):
    return subprocess.run([MYSQL,"--force",f"-u{U}","-N","-e",sql],capture_output=True,text=True,
                          env=dict(os.environ,MYSQL_PWD=PW)).stdout.strip()
def b64(d): return base64.urlsafe_b64encode(d).rstrip(b"=").decode()
perms=[x for x in mysql(f"SELECT menu_code FROM {DB}.oa_menu WHERE menu_code IS NOT NULL AND menu_code<>''").splitlines() if x]
def tok(uid,name):
    now=int(time.time()); p={"sub":name,"userId":uid,"roles":["SUPER_ADMIN"],"perms":perms,"iat":now,"exp":now+3600}
    h=b64(json.dumps({"alg":"HS384","typ":"JWT"},separators=(",",":")).encode())
    b=b64(json.dumps(p,separators=(",",":")).encode())
    return f"{h}.{b}.{b64(hmac.new(SEC,f'{h}.{b}'.encode(),hashlib.sha384).digest())}"
T=tok(1,"admin")
def req(method,path,body=None):
    data=json.dumps(body).encode() if body is not None else None
    r=urllib.request.Request(BASE+path,data=data,method=method,headers={"Content-Type":"application/json","Authorization":"Bearer "+T})
    try:
        with urllib.request.urlopen(r) as x: return json.loads(x.read().decode())
    except urllib.error.HTTPError as e:
        try: return json.loads(e.read().decode())
        except Exception: return {}
def ck(n,ok,d=""):
    R.append((n,ok)); print(f"{'PASS' if ok else 'FAIL'} | {n} | {d}")
def cleanup(ids):
    for i in ids:
        pid=mysql(f"SELECT proc_inst_id FROM {DB}.oa_approval_instance WHERE id={i}") or ""
        pin=f"'{pid}'" if pid else "''"
        mysql(" ".join(["SET FOREIGN_KEY_CHECKS=0;",
          f"DELETE FROM {DB}.oa_approval_record WHERE instance_id={i};",
          f"DELETE FROM {DB}.oa_approval_instance WHERE id={i};",
          f"DELETE FROM {DB}.act_ru_variable WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_ru_task WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_ru_identitylink WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_ru_execution WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_hi_varinst WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_hi_detail WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_hi_identitylink WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_hi_taskinst WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_hi_actinst WHERE PROC_INST_ID_={pin};",
          f"DELETE FROM {DB}.act_hi_procinst WHERE PROC_INST_ID_={pin};",
          "SET FOREIGN_KEY_CHECKS=1;"]))
def main():
    opts=req("GET","/workflow/flow-definition/options")
    rows=(opts.get("data") or [])
    exp=next((o for o in rows if o.get("flowCode")=="expense"),{})
    ck("A 选项下发分类与图标", exp.get("category")=="财务" and exp.get("icon")=="Receipt",
       f"category={exp.get('category')} icon={exp.get('icon')}")

    d1=req("POST","/workflow/approval/draft",{"flowCode":"expense","title":"草稿标题","formData":json.dumps({"amount":100})})
    did=d1.get("data"); ck("B1 保存草稿返回 id", d1.get("code")==200 and isinstance(did,int), f"code={d1.get('code')} id={did}")
    det=req("GET",f"/workflow/approval/{did}/detail").get("data") or {}
    inst=det.get("instance") or {}
    code=inst.get("billCode") or ""
    ok_code=bool(re.fullmatch(r"EXPENSE-\d{8}-\d{4,}",code))
    ck("B2 草稿状态=1 且生成单据编号", inst.get("status")==1 and ok_code, f"status={inst.get('status')} bill={code}")

    u=req("PUT",f"/workflow/approval/draft/{did}",{"flowCode":"expense","title":"改过的标题","formData":json.dumps({"amount":200})})
    det2=(req("GET",f"/workflow/approval/{did}/detail").get("data") or {}).get("instance") or {}
    ck("B3 更新草稿：标题变、编号不变", u.get("code")==200 and det2.get("title")=="改过的标题" and det2.get("billCode")==code,
       f"title={det2.get('title')} bill={det2.get('billCode')}")

    s=req("POST",f"/workflow/approval/{did}/submit")
    det3=(req("GET",f"/workflow/approval/{did}/detail").get("data") or {}).get("instance") or {}
    ck("B4 提交草稿 → 状态 2", s.get("code")==200 and det3.get("status")==2, f"code={s.get('code')} status={det3.get('status')}")
    ck("B5 提交后单据编号不变", det3.get("billCode")==code, f"bill={det3.get('billCode')}")

    s2=req("POST","/workflow/approval/start",{"flowCode":"expense","title":"直接提交","formData":"{}"})
    i2=s2.get("data")
    det4=(req("GET",f"/workflow/approval/{i2}/detail").get("data") or {}).get("instance") or {}
    ck("C 直接发起也生成编号", s2.get("code")==200 and bool(det4.get("billCode")), f"bill={det4.get('billCode')}")

    cleanup([did,i2])
    print("CLEANUP 残留=", mysql(f"SELECT COUNT(*) FROM {DB}.oa_approval_instance WHERE id IN ({did},{i2})"))
    fail=[n for n,ok in R if not ok]
    print(f"\nSUMMARY: {len(R)-len(fail)}/{len(R)} 通过")
    return 0 if not fail else 1
sys.exit(main())
