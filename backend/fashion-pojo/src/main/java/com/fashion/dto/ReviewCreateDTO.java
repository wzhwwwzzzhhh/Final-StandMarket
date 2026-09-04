package com.fashion.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户评价写入白名单；身份、状态和时间只能由服务端决定。
 */
@Data
public class ReviewCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Long productId;
    private Integer rating;
    private String content;
    private String images;
}
