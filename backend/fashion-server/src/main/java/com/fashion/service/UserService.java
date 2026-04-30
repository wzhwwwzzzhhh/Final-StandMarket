package com.fashion.service;

import com.fashion.dto.UserLoginDto;
import com.fashion.entity.User;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.vo.UserLoginVo;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * 用户服务接口
 */

public interface UserService {
    /**
     * 新增用户
     * @param user 用户信息
     */
    void save(User user);

    /**
     * 删除用户
     * @param id 用户id
     */
    void delete(Long id);

    /**
     * 更新用户信息
     * @param user 用户信息
     */
    void update(User user);

    /**
     * 根据id查询用户
     * @param id 用户id
     * @return 用户信息
     */
    User getById(Long id);

    /**
     * 分页查询用户
     * @param page 页码
     * @param pageSize 每页大小
     * @param name 用户名
     * @param phone 手机号
     * @return 分页结果
     */
    PageResult<User> pageUsers(int page, int pageSize, String name, String phone);

    /**
     * 统计用户数量
     * @return 用户数量
     */
    int count();

    //====================================================
    /**
     * 发送短信验证码
     * @param phone
     */
    Result<String> sendSmsCode(String phone);

    /**
     * 用户登录
     * @param userLoginDto
     * @param session
     * @return
     */
    Result<UserLoginVo> login(UserLoginDto userLoginDto, HttpSession session);

    /**
     * 获取用户信息
     * @param token
     * @return
     */
    Result<User> getUserInfo(String token);

    /**
     * 更新用户信息
     * @param token
     * @param user
     * @return
     */
    Result<String> updateUserInfo(String token, User user);

    /**
     * 修改密码
     * @param token 用户token
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    Result<String> changePassword(String token, String oldPassword, String newPassword);

    /**
     * 退出登录
     * @param token 用户token
     * @return 操作结果
     */
    Result<String> logout(String token);
    
    /**
     * 用户注册
     * @param user 用户信息
     * @return 操作结果
     */
    Result<String> register(User user);
}