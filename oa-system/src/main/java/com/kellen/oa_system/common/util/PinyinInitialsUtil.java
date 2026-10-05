package com.kellen.oa_system.common.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.springframework.util.StringUtils;

/**
 * 汉字拼音首字母工具（用于生成部门工号前缀）。
 *
 * <p>转换规则：逐个字符取拼音首字母并大写；ASCII 字母数字原样大写保留，
 * 其余字符（空格、标点、括号等）直接跳过。例如：</p>
 * <pre>
 *   技术部            → JSB
 *   示例科技有限公司   → SYKJYXGS
 *   HR部              → HRB
 * </pre>
 *
 * <p><b>多音字说明</b>：pinyin4j 对多音字（如「长」chang/zhang）返回多个读音，
 * 这里取第一个读音的首字母，可能与业务预期不符。因此部门工号前缀在生成后
 * 会持久化到 {@code oa_department.work_no_prefix}，管理员可在部门管理中手工纠正，
 * 已生成的工号不受影响。</p>
 */
public final class PinyinInitialsUtil {

    /** 输出格式：大写、无声调。该对象仅持有配置，读取线程安全 */
    private static final HanyuPinyinOutputFormat OUTPUT_FORMAT = new HanyuPinyinOutputFormat();

    static {
        OUTPUT_FORMAT.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        OUTPUT_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private PinyinInitialsUtil() {
    }

    /**
     * 取字符串的拼音首字母串（全大写）。
     *
     * @param text 原始文本，可为 null / 空串
     * @return 首字母串；无任何可识别字符时返回空串（调用方负责兜底）
     */
    public static String toInitials(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder initials = new StringBuilder(text.length());
        for (char ch : text.trim().toCharArray()) {
            String letter = firstLetterOf(ch);
            if (StringUtils.hasText(letter)) {
                initials.append(letter);
            }
        }
        return initials.toString();
    }

    /**
     * 取单个字符的拼音首字母；非汉字且非字母数字时返回 null。
     */
    private static String firstLetterOf(char ch) {
        if (isAsciiLetterOrDigit(ch)) {
            return String.valueOf(Character.toUpperCase(ch));
        }
        if (!isChinese(ch)) {
            return null;
        }
        try {
            String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch, OUTPUT_FORMAT);
            if (pinyins == null || pinyins.length == 0 || !StringUtils.hasText(pinyins[0])) {
                return null;
            }
            return pinyins[0].substring(0, 1);
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            // 输出格式为固定常量，正常不会触发；此处降级为跳过该字符，不阻断业务
            return null;
        }
    }

    private static boolean isAsciiLetterOrDigit(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
    }

    /**
     * 是否为 CJK 统一汉字（含扩展 A 区）。pinyin4j 只覆盖基本区，
     * 扩展区字符会被判定为非汉字而跳过，属于可接受的降级。
     */
    private static boolean isChinese(char ch) {
        return ch >= 0x4E00 && ch <= 0x9FFF;
    }
}
