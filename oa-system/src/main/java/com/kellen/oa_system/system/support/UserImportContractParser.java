package com.kellen.oa_system.system.support;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 导入表中的「合同信息 + 试用期」列解析与校验。
 *
 * <p>从行解析器里拆出来的原因很实际：这几列有 8 条相互依赖的规则
 * （类型与结束日必须匹配、开始不能晚于结束、开始不能比入职晚一年以上、
 * 试用期不能超法定上限、无固定期限不能填结束日…），和「账号/手机号/部门」
 * 那种线性校验混在一起时，改一条很容易碰坏另一条。</p>
 *
 * <p>四种合同类型的中文写法、试用期法定上限见 {@link UserContractRules}——
 * 那是纯函数，本类只负责「从单元格读出值、套用规则、给出人能读的错误」。</p>
 */
public final class UserImportContractParser {

    private UserImportContractParser() {
    }

    /**
     * 解析合同与试用期。
     *
     * @param row       数据行
     * @param entryDate 已落定的入职日期（空值已被上游补成当天），试用期截止日以它为基准
     * @return 解析结果；合同三列全空时返回 {@link ContractInfo#none()}
     * @throws BizException 任一规则不满足，message 直接面向 HR
     */
    public static ContractInfo parse(List<String> row, LocalDate entryDate) {
        String typeText = UserImportCells.text(row, UserImportTemplate.COL_CONTRACT_TYPE);
        String startText = UserImportCells.text(row, UserImportTemplate.COL_CONTRACT_START);
        String endText = UserImportCells.text(row, UserImportTemplate.COL_CONTRACT_END);
        String probationText = UserImportCells.text(row, UserImportTemplate.COL_PROBATION_MONTHS);

        if (typeText.isEmpty() && startText.isEmpty() && endText.isEmpty()) {
            // 合同信息整段留空 → 不建合同。允许这样，因为「建档完整度」会把「无在职合同」
            // 列成清单让 HR 后补；强行必填会把「只想先建账号」的场景直接堵死
            return ContractInfo.none();
        }

        Integer type = UserContractRules.parseType(typeText);
        if (type == null) {
            throw fail("合同类型「" + typeText + "」无法识别，请填写「固定期限/无固定期限/实习/劳务/返聘」");
        }

        LocalDate start = UserImportCells.date(startText);
        if (start == null) {
            throw fail(startText.isEmpty() ? "填写了合同信息时，合同开始日期不能为空"
                    : "合同开始日期「" + startText + "」格式不正确");
        }

        LocalDate end = null;
        if (!endText.isEmpty()) {
            end = UserImportCells.date(endText);
            if (end == null) {
                throw fail("合同结束日期「" + endText + "」格式不正确");
            }
        }
        if (type == UserContractRules.TYPE_OPEN) {
            if (end != null) {
                throw fail("无固定期限合同不应填写结束日期");
            }
        } else if (end == null) {
            throw fail("固定期限/实习/劳务/返聘合同必须填写结束日期");
        }
        if (end != null && !end.isAfter(start)) {
            throw fail("合同结束日期必须晚于开始日期");
        }
        if (start.isAfter(entryDate.plusYears(1))) {
            // 常见手误：把「合同结束日」填进了「开始日」，或年份写成 2062
            throw fail("合同开始日期「" + start + "」晚于入职日期一年以上，请确认是否填错");
        }

        int ceiling = UserContractRules.probationCeiling(type, start, end);
        Integer months;
        if (probationText.isEmpty()) {
            months = UserContractRules.defaultProbation(ceiling);
        } else {
            try {
                months = Integer.parseInt(probationText.trim());
            } catch (NumberFormatException e) {
                throw fail("试用期月数「" + probationText + "」不是整数");
            }
            if (months < 0 || months > 6) {
                throw fail("试用期月数只能是 0~6 之间的整数");
            }
            if (months > ceiling) {
                throw fail("试用期 " + months + " 个月超出法定上限（该合同期限最长 " + ceiling
                        + " 个月），请调整合同期限或试用期");
            }
        }
        return new ContractInfo(type, start, end, months, UserContractRules.probationEnd(entryDate, months));
    }

    private static BizException fail(String message) {
        return new BizException(ErrorCode.USER_IMPORT_FILE_INVALID, message);
    }

    /**
     * 合同解析结果。
     *
     * @param type            合同类型；null 表示不建合同
     * @param probationMonths 试用期月数（已按法定上限校验）
     * @param probationEnd    试用期截止日
     */
    public record ContractInfo(Integer type, LocalDate start, LocalDate end,
                               Integer probationMonths, LocalDate probationEnd) {

        public static ContractInfo none() {
            return new ContractInfo(null, null, null, null, null);
        }
    }
}
