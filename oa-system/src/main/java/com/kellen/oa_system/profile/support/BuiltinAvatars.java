package com.kellen.oa_system.profile.support;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 系统内置头像白名单。
 *
 * <p><b>为什么需要独立白名单</b>：内置头像随前端静态资源发布（{@code public/avatars/avatar-NN.svg}），
 * 不落在用户上传目录 {@code /uploads} 下，因此没法复用「上传前缀」那一条校验。
 * 也不能简单放行 {@code /avatars/} 前缀——那样 {@code /avatars/../..} 之类的拼接也会通过。
 * 这里改成<b>精确枚举</b>：只认登记过的文件，路径拼接自然被挡掉。</p>
 *
 * <p><b>新增内置头像</b>：前端 {@code src/constants/avatars.ts} 与这里必须同步改，
 * 否则用户在界面上能选、保存时却被后端拒绝。</p>
 */
public final class BuiltinAvatars {

    /** 静态资源目录，与前端 {@code public/avatars} 对应 */
    public static final String PATH_PREFIX = "/avatars/";

    /** 内置头像数量（文件名为 avatar-01.svg ~ avatar-12.svg） */
    public static final int COUNT = 12;

    private static final Set<String> PATHS;

    static {
        Set<String> paths = new LinkedHashSet<>(COUNT);
        for (int i = 1; i <= COUNT; i++) {
            paths.add(String.format("%savatar-%02d.svg", PATH_PREFIX, i));
        }
        PATHS = Collections.unmodifiableSet(paths);
    }

    private BuiltinAvatars() {
    }

    /** 是否为内置头像（精确匹配，非前缀判断） */
    public static boolean contains(String avatar) {
        return avatar != null && PATHS.contains(avatar);
    }

    /** 全部内置头像地址，供接口/测试与前端对齐 */
    public static Set<String> all() {
        return PATHS;
    }
}
