package com.kellen.oa_system.hr.common.enums;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;

import java.util.Map;
import java.util.Set;

/**
 * 任职状态与合法跳转。
 *
 * <p>与 {@code oa_user.status}（账号状态：0禁用 1启用 2锁定）严格区分：
 * 本枚举描述劳动关系，账号状态描述「能不能登录」。两者混用会得出
 * 「禁用账号 = 员工已离职」的错误结论，进而让花名册人数、编制统计全部失真。</p>
 */
public enum EmploymentStatusEnum {

    /** 待入职：已发 offer，未到报到日；不占编制、不可登录 */
    PENDING_ONBOARD(1, "待入职"),
    /** 试用：已入职，试用期内 */
    PROBATION(2, "试用"),
    /** 正式 */
    REGULAR(3, "正式"),
    /** 待离职：已提离职，未到最后工作日 */
    PENDING_LEAVE(4, "待离职"),
    /** 已离职：劳动关系终止，账号自动禁用 */
    LEFT(5, "已离职"),
    /** 退休 */
    RETIRED(6, "退休");

    /** 合法跳转表：key 为当前状态，value 为允许迁移到的状态集合 */
    private static final Map<EmploymentStatusEnum, Set<EmploymentStatusEnum>> TRANSITIONS = Map.of(
            PENDING_ONBOARD, Set.of(PROBATION, LEFT),
            PROBATION, Set.of(REGULAR, LEFT),
            REGULAR, Set.of(PENDING_LEAVE, LEFT, RETIRED),
            PENDING_LEAVE, Set.of(REGULAR, LEFT),
            LEFT, Set.of(),
            RETIRED, Set.of());

    /** 在职口径：试用 / 正式 / 待离职 */
    private static final Set<Integer> ON_DUTY = Set.of(2, 3, 4);

    private final Integer code;
    private final String label;

    EmploymentStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public Integer getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static EmploymentStatusEnum of(Integer code) {
        for (EmploymentStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "未知的任职状态：" + code);
    }

    /** 是否在职（2/3/4）。花名册、编制统计一律用此口径，不要用账号 status=1 */
    public static boolean isOnDuty(Integer code) {
        return code != null && ON_DUTY.contains(code);
    }

    /** 该状态是否要求账号不可登录 */
    public boolean forbidsLogin() {
        return this == LEFT || this == RETIRED || this == PENDING_ONBOARD;
    }

    /**
     * 校验状态跳转是否合法；同状态视为无变化直接放行。
     *
     * @throws BizException 非法跳转（如「已离职」直接改回「正式」）
     */
    public static void assertTransition(Integer fromCode, Integer toCode) {
        if (fromCode == null) {
            return;
        }
        if (fromCode.equals(toCode)) {
            return;
        }
        EmploymentStatusEnum from = of(fromCode);
        EmploymentStatusEnum to = of(toCode);
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BizException(ErrorCode.HR_EMPLOYMENT_STATUS_INVALID,
                    "不允许从「" + from.label + "」变更为「" + to.label + "」");
        }
    }
}
