package com.fashion.vo;

import lombok.Data;

/**
 * 管理端登录结果
 */
@Data
public class AdminLoginVo {
    private String token;
    private Long employeeId;
    private String name;
}
