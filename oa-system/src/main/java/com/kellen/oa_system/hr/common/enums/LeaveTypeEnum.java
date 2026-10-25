package com.kellen.oa_system.hr.common.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 假别枚举。
 *
 * <p>与 {@code LeaveServiceImpl} 内部的历史标签表口径一致（1~8）。
 * 这里额外承载「是否受地方政策限制」这一属性：只有婚假/产假/陪产假/丧假由地方规定天数，
 * 其余假别走额度或按单据天数，不应出现在地方政策配置页里。</p>
 */
public enum LeaveTypeEnum {

    PERSONAL(1, "事假", false),
    SICK(2, "病假", false),
    ANNUAL(3, "年假", false),
    MARRIAGE(4, "婚假", true),
    MATERNITY(5, "产假", true),
    PATERNITY(6, "陪产假", true),
    BEREAVEMENT(7, "丧假", true),
    COMPENSATORY(8, "调休", false);

    private final int code;
    private final String label;
    /** 是否由地方政策规定天数 */
    private final boolean policyControlled;

    LeaveTypeEnum(int code, String label, boolean policyControlled) {
        this.code = code;
        this.label = label;
        this.policyControlled = policyControlled;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPolicyControlled() {
        return policyControlled;
    }

    /** 受地方政策限制的假别编码集合 */
    public static Set<Integer> policyControlledCodes() {
        return Arrays.stream(values())
                .filter(LeaveTypeEnum::isPolicyControlled)
                .map(LeaveTypeEnum::getCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 受地方政策限制的假别列表（供前端下拉） */
    public static List<LeaveTypeEnum> policyControlled() {
        return Arrays.stream(values()).filter(LeaveTypeEnum::isPolicyControlled).toList();
    }

    public static Integer codeOf(String label) {
        return Arrays.stream(values())
                .filter(e -> e.label.equals(label))
                .map(LeaveTypeEnum::getCode)
                .findFirst().orElse(null);
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .map(LeaveTypeEnum::getLabel)
                .findFirst().orElse(null);
    }
}
