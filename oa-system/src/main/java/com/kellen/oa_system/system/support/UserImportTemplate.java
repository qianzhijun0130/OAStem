package com.kellen.oa_system.system.support;

import com.kellen.oa_system.common.util.ExcelImportUtil;

import java.util.List;

/**
 * 员工批量导入模板的表头定义与列序号（BIZ-OPT-09）。
 *
 * <p>为什么把列定义单独成类：下载模板、解析导入、旧模板识别三处都要用同一份列顺序。
 * 散在 Service 里改一处漏一处的结果是「下载的模板导不进去」，而报错信息只会说
 * 「第 12 列表头应为 X」，HR 完全无从下手。</p>
 *
 * <h2>列数为什么是 18 而不是方案里写的 16</h2>
 * <p>方案要求新增「身份证号、合同类型、合同起止、试用期月数、参加工作日期、
 * 直属上级工号、任职状态」共 6 类字段，但「合同起止」在表格里必须是两个日期列
 * （合成一格写 {@code 2026-01-01~2027-12-31} 会让 HR 反复踩格式错），
 * 所以实际是 10 + 8 = 18 列。列数以能表达全部字段为准——少两列意味着
 * 合同信息还得回档案页逐人补录，那就没解决「百人入职要 8 小时」的问题。</p>
 *
 * <h2>旧模板怎么办</h2>
 * <p>方案要求「新旧模板并行一个版本期」：识别到旧 10 列表头时，
 * 抛出的是<b>「请下载新模板」</b>而不是「第 11 列表头应为…」。
 * 两者都拒绝导入，但前者 HR 一看就知道该怎么办，后者只会让人怀疑系统坏了。</p>
 */
public final class UserImportTemplate {

    // ---------------- 列序号（与 templateHeaders 顺序严格对应） ----------------
    public static final int COL_USERNAME = 0;
    public static final int COL_REAL_NAME = 1;
    public static final int COL_MOBILE = 2;
    public static final int COL_DEPT = 3;
    public static final int COL_POSITION = 4;
    public static final int COL_EMAIL = 5;
    public static final int COL_GENDER = 6;
    public static final int COL_ENTRY_DATE = 7;
    public static final int COL_WORK_NUMBER = 8;
    public static final int COL_ROLES = 9;
    public static final int COL_ID_CARD = 10;
    public static final int COL_CONTRACT_TYPE = 11;
    public static final int COL_CONTRACT_START = 12;
    public static final int COL_CONTRACT_END = 13;
    public static final int COL_PROBATION_MONTHS = 14;
    public static final int COL_WORK_START_DATE = 15;
    public static final int COL_LEADER_WORK_NUMBER = 16;
    public static final int COL_EMPLOYMENT_STATUS = 17;

    /** 旧版模板的列数（只有前 10 列） */
    public static final int LEGACY_COLUMN_COUNT = 10;

    private static final List<String> HEADERS = List.of(
            "登录账号", "姓名", "手机号", "部门", "岗位", "邮箱", "性别", "入职日期", "工号", "角色",
            "身份证号", "合同类型", "合同开始日期", "合同结束日期", "试用期月数",
            "参加工作日期", "直属上级工号", "任职状态");

    private UserImportTemplate() {
    }

    /** 表头（顺序即列序号） */
    public static List<String> headers() {
        return HEADERS;
    }

    /**
     * 校验表头并区分新旧版本。
     *
     * @return 不做返回值，仅做校验；不通过时抛 {@code BizException}
     */
    public static void assertHeader(List<String> header) {
        if (looksLikeLegacy(header)) {
            throw new com.kellen.oa_system.common.BizException(
                    com.kellen.oa_system.common.ErrorCode.USER_IMPORT_FILE_INVALID,
                    "检测到这是旧版 10 列模板，已不再支持。请点「下载模板」获取新版模板"
                            + "（新增身份证号、合同信息、试用期、参加工作日期、直属上级、任职状态），"
                            + "把原内容按新表头粘贴过来即可");
        }
        for (int i = 0; i < HEADERS.size(); i++) {
            String actual = ExcelImportUtil.cellAt(header, i);
            if (!HEADERS.get(i).equals(actual)) {
                throw new com.kellen.oa_system.common.BizException(
                        com.kellen.oa_system.common.ErrorCode.USER_IMPORT_FILE_INVALID,
                        "第 " + (i + 1) + " 列表头应为「" + HEADERS.get(i)
                                + "」，请下载系统模板后重新填写");
            }
        }
    }

    /**
     * 是否旧版模板：前 10 列表头与旧版一致，且第 11 列（身份证号）不是新表头。
     *
     * <p>只比前 10 列而不比「总列数等于 10」，是因为 HR 常在旧模板右侧自己加备注列；
     * 那种文件仍算旧模板，应当提示「请下载新模板」，而不是报「第 11 列表头应为身份证号」。</p>
     */
    public static boolean looksLikeLegacy(List<String> header) {
        for (int i = 0; i < LEGACY_COLUMN_COUNT; i++) {
            if (!HEADERS.get(i).equals(ExcelImportUtil.cellAt(header, i))) {
                return false;
            }
        }
        String col11 = ExcelImportUtil.cellAt(header, COL_ID_CARD);
        return !HEADERS.get(COL_ID_CARD).equals(col11);
    }

    /** 模板「填写说明」工作表内容 */
    public static List<String> notes() {
        return List.of(
                "填写说明：",
                "1. 请勿修改「员工数据」表的表头文字与列顺序，否则将按格式错误拒绝导入。",
                "2. 登录账号、姓名、手机号、部门为必填项；身份证号、合同信息强烈建议填写（不填会在「建档完整度」里显示为缺失）。",
                "3. 部门填部门名称（如「技术部」）或部门编码，需与系统内已有部门完全一致。",
                "4. 性别填「男」或「女」，留空默认男；入职日期留空默认取导入当天。",
                "5. 工号留空由系统按部门自动生成（部门拼音首字母 + 流水号），填写则以填写值为准。",
                "6. 角色可填多个，用英文逗号分隔（如「普通员工,人事专员」）；留空默认分配「普通员工」。",
                "7. 身份证号需为 18 位且校验码正确；同一身份证只能建档一次，重复会提示已存在的工号。",
                "8. 合同类型填「固定期限 / 无固定期限 / 实习 / 劳务 / 返聘」，留空默认「固定期限」。",
                "9. 合同开始/结束日期格式如 2026-09-29；无固定期限可不填结束日期。",
                "10. 试用期月数留空时按合同期限自动取法定上限（3 个月~1 年取 1 个月，1~3 年取 2 个月，3 年以上或无固定期限取默认 3 个月）；",
                "     填写值不得超过法定上限，试用期截止日由系统按「入职日 + 月数 − 1 天」自动生成。",
                "11. 参加工作日期用于年假累计工龄计算，留空则按入职日期算。",
                "12. 直属上级工号填系统中已存在员工的工号；留空则按部门负责人推导。",
                "13. 任职状态填「待入职 / 试用 / 正式」，留空时按入职日期自动判定（未到入职日为待入职，否则试用）；",
                "     任职状态为「待入职」的账号默认不可登录，报到后请在「员工档案」里改为「试用」。",
                "14. 新增员工的初始密码统一为系统默认初始密码，导入后请提醒员工尽快修改。",
                "15. 校验失败的行会跳过并返回行号与原因，其余行照常入库（不做整批回滚）。",
                "16. 单次导入不超过 " + ExcelImportUtil.MAX_DATA_ROWS + " 行。");
    }
}
