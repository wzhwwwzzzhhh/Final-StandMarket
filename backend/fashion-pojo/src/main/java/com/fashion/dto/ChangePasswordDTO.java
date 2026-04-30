package com.fashion.dto;

import lombok.Data;

/**
 * 修改密码参数DTO
 */
@Data
public class ChangePasswordDTO {
    private String oldPassword;
    private String newPassword;
}
