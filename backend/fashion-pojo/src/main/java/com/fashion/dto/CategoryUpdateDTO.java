package com.fashion.dto;

import lombok.Data;

/**
 * 分类更新参数DTO
 */
@Data
public class CategoryUpdateDTO {
    private Long id;
    private String name;
    private Integer type;
    private Integer sort;
    private Integer status;
}
