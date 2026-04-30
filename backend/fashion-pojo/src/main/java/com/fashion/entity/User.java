package com.fashion.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    // 用户id
    private Long id;
    // 微信openid
    private String openid;
    // 用户名
    private String name;
    // 手机号
    private String phone;
    // 密码
    private String password;
    // 性别
    private String sex;
    // 身份证号
    private String idNumber;
    // 头像
    private String avatar;
    // 创建时间
    private LocalDateTime createTime;


}