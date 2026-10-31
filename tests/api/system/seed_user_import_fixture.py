"""
BIZ-OPT-09 UI 用例的夹具：生成一份「1 行正确 + 1 行手机号非法」的导入文件。

为什么要在 python 侧生成：node 那边没有 openpyxl，而手写 xlsx（zip + xml）容易出错。
顺带也方便在用例结束时清理——同一个脚本加 `clean` 参数即可。

用法：
    python seed_user_import_fixture.py          # 生成文件，打印绝对路径
    python seed_user_import_fixture.py clean    # 清掉用例造的用户
"""
import io
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from xlib import DB, mysql, mysql_exec  # noqa: E402

from openpyxl import Workbook  # noqa: E402

HEADERS = ["登录账号", "姓名", "手机号", "部门", "岗位", "邮箱", "性别", "入职日期", "工号", "角色",
           "身份证号", "合同类型", "合同开始日期", "合同结束日期", "试用期月数",
           "参加工作日期", "直属上级工号", "任职状态"]

PREFIX = "IMPV_UI_"

W = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
C = "10X98765432"


def id_card(prefix17: str) -> str:
    return prefix17 + C[sum(int(prefix17[i]) * W[i] for i in range(17)) % 11]


def cleanup():
    ids = mysql(f"SELECT GROUP_CONCAT(id) FROM {DB}.oa_user WHERE username LIKE '{PREFIX}%'")
    if ids:
        mysql_exec(f"DELETE FROM {DB}.oa_employee_contract WHERE user_id IN ({ids})")
        mysql_exec(f"DELETE FROM {DB}.oa_user_role WHERE user_id IN ({ids})")
        mysql_exec(f"DELETE FROM {DB}.oa_user WHERE id IN ({ids})")
    return ids or "0"


def build_file() -> Path:
    out_dir = Path(__file__).resolve().parents[3] / "tests" / ".runtime"
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / "user_import_ui.xlsx"

    wb = Workbook()
    ws = wb.active
    ws.title = "员工数据"
    ws.append(HEADERS)
    # 第 2 行：完整合法 → 成功
    ws.append([f"{PREFIX}OK", "界面甲", "13800003001", "技术部", "工程师", "", "男", "2026-11-01", "",
               "普通员工", id_card("11010119900307233"), "固定期限", "2026-11-01", "2027-11-01", "2",
               "2019-06-01", "", ""])
    # 第 3 行：手机号非法 → 失败（用于验证「部分成功」对话框）
    ws.append([f"{PREFIX}BAD", "界面乙", "123", "技术部", "工程师", "", "女", "2026-11-01", "",
               "普通员工", id_card("11010119900307234"), "固定期限", "2026-11-01", "2027-11-01", "2",
               "", "", ""])
    buf = io.BytesIO()
    wb.save(buf)
    path.write_bytes(buf.getvalue())
    return path


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "clean":
        print(f"cleaned={cleanup()}")
    else:
        cleanup()
        print(build_file())
