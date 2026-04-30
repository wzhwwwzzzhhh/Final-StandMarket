package com.fashion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginDto {
    /**
     * 手机号 短信或者密码登录
     * @required
     */
    private String phone;
    private String password;
    private String code;
    private String type;


}
