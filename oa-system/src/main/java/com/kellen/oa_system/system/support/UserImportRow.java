package com.kellen.oa_system.system.support;

import com.kellen.oa_system.hr.common.enums.EmploymentStatusEnum;
import com.kellen.oa_system.system.dto.UserSaveRequest;

import java.time.LocalDate;

/**
 * 一行导入数据的解析结果（内部载体，不对外）。
 *
 * <p>为什么不直接把 {@link UserSaveRequest} 传下去：合同信息（类型/起止/试用期月数）
 * 不属于「用户」这张表，它要写进 {@code oa_employee_contract}。把它们塞进保存请求
 * 会让「保存用户」这个方法看起来也会建合同，而实际上手工新增并不会。</p>
 *
 * @param rowNo          Excel 行号（含表头，从 1 开始），错误提示直接引用它
 * @param request        用户保存请求（已含身份证、上级、参加工作日期、试用期截止日、任职状态）
 * @param contractType   合同类型 1固定期限 2无固定期限 3实习 4劳务 5返聘；null 表示不建合同
 * @param contractStart  合同开始日期
 * @param contractEnd    合同结束日期（无固定期限为 null）
 * @param probationMonths 试用期月数（已按法定上限校验）
 */
public record UserImportRow(int rowNo,
                            UserSaveRequest request,
                            Integer contractType,
                            LocalDate contractStart,
                            LocalDate contractEnd,
                            Integer probationMonths) {

    /** 合同类型中文名，用于结果提示 */
    public String contractTypeLabel() {
        return switch (contractType == null ? 0 : contractType) {
            case 1 -> "固定期限";
            case 2 -> "无固定期限";
            case 3 -> "实习";
            case 4 -> "劳务";
            case 5 -> "返聘";
            default -> "无";
        };
    }

    /** 任职状态中文名，用于结果提示 */
    public String employmentStatusLabel() {
        Integer code = request.getEmploymentStatus();
        return code == null ? "未设置" : EmploymentStatusEnum.of(code).getLabel();
    }
}
