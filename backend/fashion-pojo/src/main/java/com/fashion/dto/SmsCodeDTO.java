package com.fashion.dto;

import lombok.Data;

/**
 * 发送验证码参数DTO
 */
@Data
public class SmsCodeDTO {
    private String phone;
}
