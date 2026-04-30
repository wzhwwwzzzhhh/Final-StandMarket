package com.fashion.dto;

import lombok.Data;

/**
 * 分类保存参数DTO
 */
@Data
public class CategorySaveDTO {
    private String name;
    private Integer type;
    private Integer sort;
    private Integer status;
}
