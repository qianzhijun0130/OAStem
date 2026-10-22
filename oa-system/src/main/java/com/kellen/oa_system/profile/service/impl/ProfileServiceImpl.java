package com.kellen.oa_system.profile.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.file.config.FileStorageProperties;
import com.kellen.oa_system.profile.dto.PasswordChangeRequest;
import com.kellen.oa_system.profile.dto.ProfileUpdateRequest;
import com.kellen.oa_system.profile.service.ProfileService;
import com.kellen.oa_system.profile.support.BuiltinAvatars;
import com.kellen.oa_system.profile.vo.ProfileVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 个人中心实现。
 *
 * <p>所有方法都以 {@code SecurityContextUtil.getCurrentUserId()} 作为唯一操作对象，
 * 接口不暴露 userId，从设计上排除越权修改他人资料的可能。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageProperties fileStorageProperties;

    @Override
    public ProfileVO getProfile() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        User user = requireUser(userId);

        ProfileVO vo = new ProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setWorkNumber(user.getWorkNumber());
        vo.setPosition(user.getPosition());
        vo.setEntryDate(user.getEntryDate());
        vo.setDeptId(user.getDeptId());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setRoles(userService.getRoleCodesByUserId(userId));
        vo.setDeptName(resolveDeptName(user.getDeptId()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(ProfileUpdateRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        requireUser(userId);

        User update = new User();
        update.setId(userId);
        // 逐字段判断而非整体覆盖：MyBatis-Plus 默认忽略 null 字段，
        // 因此「没传」不会被误当成「清空」，明确传空串才会清空
        if (request.getNickname() != null) {
            update.setNickname(request.getNickname());
        }
        if (request.getGender() != null) {
            update.setGender(request.getGender());
        }
        if (request.getMobile() != null) {
            update.setMobile(request.getMobile());
        }
        if (request.getEmail() != null) {
            update.setEmail(request.getEmail());
        }
        userMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(String avatar) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        requireUser(userId);
        assertAvatarPath(avatar);

        User update = new User();
        update.setId(userId);
        update.setAvatar(avatar);
        userMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        User user = requireUser(userId);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_NOT_MATCH);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能与原密码相同");
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(update);

        // 只记录「谁改了密码」，绝不把密码本身写进日志
        log.info("用户修改密码成功 userId={}", userId);
    }

    // ---------- 内部方法 ----------

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 头像只允许两种来源：本站上传产物（{@code /uploads/**}）或系统内置头像（{@code /avatars/avatar-NN.svg}）。
     *
     * <p>不做校验等于开放一个任填 URL 的字段：既可能填入失效图，也可能被当作图床/引流入口。
     * 内置头像走 {@link BuiltinAvatars} 的精确白名单，顺带把 {@code ../} 之类的路径拼接挡掉。</p>
     */
    private void assertAvatarPath(String avatar) {
        if (!StringUtils.hasText(avatar)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "头像地址不能为空");
        }
        if (BuiltinAvatars.contains(avatar)) {
            return;
        }
        String prefix = fileStorageProperties.getAccessPrefix() + "/";
        if (!avatar.startsWith(prefix) || avatar.contains("..")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "头像地址非法，请选择系统头像或通过上传接口获取");
        }
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        Department department = departmentMapper.selectById(deptId);
        return department == null ? null : department.getDeptName();
    }
}
