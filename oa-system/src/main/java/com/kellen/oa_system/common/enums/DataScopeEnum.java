package com.kellen.oa_system.common.enums;

import lombok.Getter;

/**
 * 数据权限范围。
 *
 * <p>与 {@code oa_role.data_scope} 的取值一一对应。数值越小范围越宽，
 * 因此多角色叠加时取「最小值」即为最宽范围（见 {@code DataScopeSupport}）。</p>
 */
@Getter
public enum DataScopeEnum {

    /** 全部数据：不做任何部门/人员限制 */
    ALL(1, "全部数据"),

    /** 本部门：仅限当前用户所属部门 */
    DEPT(2, "本部门"),

    /** 仅本人：仅限当前用户自身 */
    SELF(3, "仅本人");

    private final int code;
    private final String label;

    DataScopeEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按 code 解析；无法识别时返回最窄范围 {@link #SELF}。
     *
     * <p>取最窄而非最宽，是因为数据权限属于安全边界：遇到无法识别的配置时
     * 宁可少给权限，也不能因脏数据放开可见范围。</p>
     */
    public static DataScopeEnum parse(Integer code) {
        if (code != null) {
            for (DataScopeEnum item : values()) {
                if (item.code == code) {
                    return item;
                }
            }
        }
        return SELF;
    }
}
