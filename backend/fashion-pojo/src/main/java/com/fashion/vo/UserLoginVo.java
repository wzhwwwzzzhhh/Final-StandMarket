package com.fashion.vo;

import lombok.Data;

@Data
public class UserLoginVo {

    private String token;
    private UserInfo userInfo;
}
