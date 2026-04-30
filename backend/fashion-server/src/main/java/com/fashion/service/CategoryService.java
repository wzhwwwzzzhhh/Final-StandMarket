package com.fashion.service;

import com.fashion.entity.Category;
import com.fashion.entity.PageResult;
import java.util.List;
import java.util.Map;

public interface CategoryService {
    /**
     * 条件查询
     * @param params 查询参数
     * @return 分类列表
     */
    List<Category> selectByCondition(Map<String, Object> params);
    
    /**
     * 查询所有分类
     * @return 分类列表
     */
    List<Category> list();
    
    /**
     * 分页查询分类
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页后的分类列表
     */
    PageResult<Category> pageCategories(int page, int pageSize);
    
    /**
     * 根据类型查询分类
     * @param type 分类类型
     * @return 分类列表
     */
    List<Category> listByType(Integer type);
    
    /**
     * 新增分类
     * @param category 分类信息
     * @return 是否成功
     */
    boolean save(Category category);
    
    /**
     * 更新分类
     * @param category 分类信息
     * @return 是否成功
     */
    boolean update(Category category);
    
    /**
     * 删除分类
     * @param id 分类ID
     * @return 是否成功
     */
    boolean removeById(Long id);


    /**
     * 根据ID查询分类
     * @param id 分类ID
     * @return 分类信息
     */
    Category getById(Long id);
}
