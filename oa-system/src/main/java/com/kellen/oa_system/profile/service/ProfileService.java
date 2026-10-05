package com.kellen.oa_system.profile.service;

import com.kellen.oa_system.profile.dto.PasswordChangeRequest;
import com.kellen.oa_system.profile.dto.ProfileUpdateRequest;
import com.kellen.oa_system.profile.vo.ProfileVO;

/**
 * 个人中心服务：当前登录用户的资料查看与自助维护。
 *
 * <p>与员工管理的区别：这里所有操作都<b>只能作用于当前登录用户</b>，不接受 userId 参数，
 * 从接口形态上杜绝越权改他人资料的可能。</p>
 */
public interface ProfileService {

    ProfileVO getProfile();

    void updateProfile(ProfileUpdateRequest request);

    /** @param avatar 由 /file/upload 返回的相对地址 */
    void updateAvatar(String avatar);

    /**
     * 修改密码。
     *
     * @throws com.kellen.oa_system.common.BizException 原密码错误或新旧密码相同
     */
    void changePassword(PasswordChangeRequest request);
}
