package com.fashion.dto;

import lombok.Data;

/**
 * 商品查询参数DTO
 */
@Data
public class ProductQueryDTO {
    //默认设为第1页
    private int page = 1;
    //默认设为每页10条
    private int pageSize = 10;

    private Long categoryId;
    //默认设为按创建时间排序
    private String sortBy = "createTime";
    //默认设为空
    private String keyword = "";
    //默认设为空
    private String tag = "";

    //是否起售
    //管理端查询时，根据是否起售进行筛选
    //默认为null，表示不进行筛选
    //用户端查询时，必须起售才能显示
    private Boolean isSale = null;
}
