package com.fashion.mapper;

import com.fashion.entity.Category;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface CategoryMapper {
    /**
     * 条件查询
     * @param params 查询参数
     * @return 分类列表
     */
    List<Category> selectByCondition(Map<String, Object> params);
    
    /**
     * 获取所有分类
     * @return 分类列表
     */
    List<Category> list();
    
    /**
     * 根据类型查询分类
     * @param type 分类类型
     * @return 分类列表
     */
    List<Category> listByType(Integer type);
    
    /**
     * 新增分类
     * @param category 分类信息
     * @return 影响行数
     */
    int save(Category category);
    
    /**
     * 更新分类
     * @param category 分类信息
     * @return 影响行数
     */
    int update(Category category);
    
    /**
     * 删除分类
     * @param id 分类ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 根据ID查询分类
     * @param id 分类ID
     * @return 分类信息
     */
    Category selectById(Long id);
}
