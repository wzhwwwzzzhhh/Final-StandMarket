package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端操作日志（审计）
 */
@Data
public class OperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 操作人ID */
    private Long employeeId;

    /** 操作人姓名 */
    private String employeeName;

    /** 模块 */
    private String module;

    /** 操作描述 */
    private String operation;

    /** HTTP方法 + 请求URI */
    private String method;

    /** 请求参数(JSON) */
    private String params;

    /** 操作IP */
    private String ip;

    /** 操作时间 */
    private LocalDateTime createTime;
}
