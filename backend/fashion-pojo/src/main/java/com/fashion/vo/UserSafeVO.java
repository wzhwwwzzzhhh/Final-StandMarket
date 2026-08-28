package com.fashion.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不包含密码或密码哈希的用户响应。
 */
@Data
public class UserSafeVO {
    private Long id;
    private String openid;
    private String name;
    private String phone;
    private String sex;
    private String idNumber;
    private String avatar;
    private LocalDateTime createTime;
    private boolean hasPassword;
}
