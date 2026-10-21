package com.kellen.oa_system.system.service;

import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.system.dto.UserPageQuery;
import com.kellen.oa_system.system.dto.UserSaveRequest;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.vo.UserVO;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

/**
 * 用户领域服务。
 */
public interface UserService {

    /**
     * 按登录账号加载用户及其全部角色、权限码。
     *
     * @throws UsernameNotFoundException 账号不存在
     */
    LoginUser loadLoginUser(String username);

    /** 查询用户拥有的角色编码（仅启用状态） */
    List<String> getRoleCodesByUserId(Long userId);

    /** 查询用户拥有的菜单/按钮权限码（仅启用状态） */
    List<String> getMenuCodesByUserId(Long userId);

    /** 记录最后登录时间与 IP */
    void updateLoginInfo(Long userId, String ip);

    /** 按主键查询用户，不存在返回 null */
    User getById(Long userId);

    /** 分页查询员工列表（含部门名与角色名） */
    com.kellen.oa_system.common.PageResult<UserVO> pageUsers(UserPageQuery query);

    /** 候选用户列表（启用态），供审批转办 / 审批人 / 通知接收人选择，不依赖 system:user:* */
    List<UserVO> listCandidates();

    /** 新增或修改员工，返回用户ID（id 为空为新增） */
    Long saveUser(UserSaveRequest request);

    /** 删除员工（逻辑删除，禁止删除当前登录账号） */
    void deleteUser(Long id);

    /** 修改员工状态（启用 / 禁用 / 锁定），禁止操作当前登录账号 */
    void updateUserStatus(Long id, Integer status);

    /** 重置员工密码为系统默认初始密码 */
    void resetPassword(Long id);
}
