package com.fashion.dto;

import lombok.Data;

/**
 * 用户信息更新参数DTO
 */
@Data
public class UserUpdateDTO {
    private String name;
    private String avatar;
    private String sex;
}
