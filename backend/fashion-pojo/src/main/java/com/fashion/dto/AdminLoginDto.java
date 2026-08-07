package com.fashion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端登录参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminLoginDto {
    private String username;
    private String password;
}
