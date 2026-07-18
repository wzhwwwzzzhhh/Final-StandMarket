package com.fashion.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class Review implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long productId;
    private Long orderId;
    private Integer rating;
    private String content;
    private String images;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 非数据库字段 - 用户名（关联查询用） */
    private String userName;

    /** 非数据库字段 - 商品名（关联查询用） */
    private String productName;
}
