package com.fashion.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 分页结果封装类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {
    
    /**
     * 总记录数
     */
    private long total;
    
    /**
     * 分页数据
     */
    private List<T> records;
    

}