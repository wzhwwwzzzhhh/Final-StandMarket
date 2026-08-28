package com.fashion.dto;

import lombok.Data;

/**
 * 设置或修改密码的专用请求。
 */
@Data
public class PasswordChangeDTO {
    private String oldPassword;
    private String newPassword;
}
