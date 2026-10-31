package com.kellen.oa_system.system.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.IdCardUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 身份证的格式校验、盲索引计算与建档查重。
 *
 * <p>为什么集中在一处：身份证有三个写入点（档案编辑、批量导入、后续可能的外部对接），
 * 每个点都要做同样三件事——验格式、算哈希、查重。分开写必然出现「导入查了、编辑没查」，
 * 于是重复档案只能从其中一条路径进来，而一旦进来就没有入口能发现它。</p>
 *
 * <p>查重为什么用哈希而不是明文：{@code oa_user.id_card} 是 AES-GCM 密文，
 * 每次加密随机 IV，同一号码两次写入密文不同，{@code WHERE id_card = ?} 永远不命中。
 * 盲索引（{@link IdCardUtil#hash}）是确定性摘要，落列 {@code id_card_hash} 后可等值查询。</p>
 *
 * <p>刻意<b>排除已逻辑删除</b>的用户：删除意味着档案已作废，同一个人重新入职
 * 应当允许重新建档，而不是被判「身份证已存在」而卡住。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdCardGuard {

    private final UserMapper userMapper;

    /**
     * 校验格式并返回盲索引；空值返回 null（表示「未填写」，不报错）。
     *
     * @throws BizException 格式不合法
     */
    public String hashOfNullable(String plainIdCard) {
        if (plainIdCard == null || plainIdCard.isBlank()) {
            return null;
        }
        if (!IdCardUtil.isValid(plainIdCard)) {
            throw new BizException(ErrorCode.ID_CARD_INVALID,
                    "身份证号「" + IdCardUtil.normalize(plainIdCard) + "」格式不正确（应为 18 位且校验码正确）");
        }
        return IdCardUtil.hash(plainIdCard);
    }

    /**
     * 查重：若该身份证已被其他档案占用则抛错，消息里带已存在档案的工号与姓名。
     *
     * <p>带工号是刻意的——HR 看到「已存在档案」只能知道重复了，看到工号才能直接打开那一条
     * 判断是「确实重复建档」还是「错填了别人的号」。</p>
     *
     * @param plainIdCard   明文身份证号，空则直接放行
     * @param excludeUserId 排除自身（编辑场景），新增传 null
     */
    public void assertNotDuplicated(String plainIdCard, Long excludeUserId) {
        String hash = hashOfNullable(plainIdCard);
        if (hash == null) {
            return;
        }
        User existing = findHolder(hash, excludeUserId);
        if (existing == null) {
            return;
        }
        throw new BizException(ErrorCode.ID_CARD_DUPLICATE,
                "该身份证已存在档案（工号 " + display(existing.getWorkNumber())
                        + "，姓名 " + display(existing.getRealName()) + "），请确认是否为重复建档");
    }

    /**
     * 批量导入用：不抛异常，命中重复时返回已存在的档案，交给调用方组装逐行错误。
     */
    public User findHolder(String hash, Long excludeUserId) {
        if (hash == null) {
            return null;
        }
        List<User> hits = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getWorkNumber, User::getRealName)
                .eq(User::getIdCardHash, hash)
                // LambdaQueryWrapper 会自动追加 is_deleted = 0；这里再显式写一次是为了
                // 让「已删除不算占用」这条业务规则在代码里看得见，而不是依赖框架默认行为
                .eq(User::getIsDeleted, 0)
                .ne(excludeUserId != null, User::getId, excludeUserId)
                .last("LIMIT 1"));
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        return hits.get(0);
    }

    private static String display(String text) {
        return text == null || text.isBlank() ? "未填" : text;
    }
}
