package com.fashion.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不包含密码或密码哈希的员工响应。
 */
@Data
public class EmployeeSafeVO {
    private Long id;
    private String name;
    private String username;
    private String phone;
    private String sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}
