package com.fashion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 用户信息更新参数DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUpdateDTO {
    private String name;
    private String avatar;
    private String sex;
}
