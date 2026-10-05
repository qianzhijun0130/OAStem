package com.kellen.oa_system.system.enums;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 性别枚举。
 *
 * <p>对应 {@code oa_user.gender}。历史字典中曾存在「未知(0)」，
 * 业务上已取消该选项，因此本枚举不再保留 UNKNOWN；存量数据由
 * {@code oa-system/数据库/user_module_fix_20260929.sql} 统一刷为「男」。</p>
 */
@Getter
public enum GenderEnum {

    /** 男 */
    MALE(1, "男"),

    /** 女 */
    FEMALE(2, "女");

    private final Integer code;

    private final String label;

    GenderEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 新建用户未指定性别时的默认值 */
    public static Integer defaultCode() {
        return MALE.code;
    }

    /**
     * 把 Excel 导入单元格中的性别文本解析为枚举。
     *
     * <p>同时接受中文标签与数字编码，兼容「男 / 女 / 1 / 2」四种写法。</p>
     *
     * <p>刻意<b>返回 null 而不是抛异常</b>：批量导入需要把「第 N 行性别填错」
     * 收集成行级错误一次性返回，抛异常会中断整批解析、只能报出第一行的问题。</p>
     *
     * @param text 单元格文本，允许为空
     * @return 匹配的枚举；文本为空或无法识别时返回 null
     */
    public static GenderEnum parse(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        for (GenderEnum gender : values()) {
            if (gender.label.equals(value) || String.valueOf(gender.code).equals(value)) {
                return gender;
            }
        }
        return null;
    }
}
