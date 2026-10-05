package com.kellen.oa_system.profile.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.profile.dto.PasswordChangeRequest;
import com.kellen.oa_system.profile.dto.ProfileAvatarRequest;
import com.kellen.oa_system.profile.dto.ProfileUpdateRequest;
import com.kellen.oa_system.profile.service.ProfileService;
import com.kellen.oa_system.profile.vo.ProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心接口。
 *
 * <p>全部作用于当前登录用户，因此不挂按钮权限码——只要登录成功就应有维护自己资料的权利。
 * 登录态由 SecurityConfig 的 {@code anyRequest().authenticated()} 统一保证。</p>
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/info")
    public Result<ProfileVO> info() {
        return Result.success(profileService.getProfile());
    }

    @PutMapping("/update")
    @OperationLog(module = "个人中心", operation = "更新个人资料")
    public Result<Void> update(@Valid @RequestBody ProfileUpdateRequest request) {
        profileService.updateProfile(request);
        return Result.success();
    }

    @PutMapping("/avatar")
    public Result<Void> updateAvatar(@Valid @RequestBody ProfileAvatarRequest request) {
        profileService.updateAvatar(request.getAvatar());
        return Result.success();
    }

    @PutMapping("/password")
    @OperationLog(module = "个人中心", operation = "修改登录密码", saveParams = false)
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        profileService.changePassword(request);
        return Result.success();
    }
}
