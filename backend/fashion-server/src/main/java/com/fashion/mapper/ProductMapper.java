package com.fashion.mapper;

import com.fashion.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper {
    /**
     * 条件查询
     * @param params 查询参数
     * @return 商品列表
     */
    List<Product> selectByCondition(Map<String, Object> params);
    
    /**
     * 根据名称查询商品列表
     * @param name 商品名称
     * @return 商品列表
     */
    List<Product> listProducts(String name);
    
    /**
     * 多条件查询商品列表
     * @param keyword 关键词
     * @param categoryId 分类ID
     * @param tag 标签
     * @param sortBy 排序方式
     * @param isSale 是否起售
     * @return 商品列表
     */
    List<Product> listProductsByCondition(@Param("keyword") String keyword, @Param("categoryId") Long categoryId, @Param("tag") String tag, @Param("sortBy") String sortBy, @Param("isSale") Boolean isSale);
    
    /**
     * 根据ID查询商品
     * @param id 商品ID
     * @return 商品
     */
    Product getById(Long id);
    
    /**
     * 新增商品
     * @param product 商品信息
     * @return 影响行数
     */
    int save(Product product);
    
    /**
     * 更新商品
     * @param product 商品信息
     * @return 影响行数
     */
    int update(Product product);
    
    /**
     * 删除商品
     * @param id 商品ID
     * @return 影响行数
     */
    int deleteById(Long id);
    
    /**
     * 根据分类ID查询商品列表
     * @param categoryId 分类ID
     * @return 商品列表
     */
    List<Product> listByCategoryId(Long categoryId);
    
    /**
     * 根据标签查询商品列表
     * @param tag 标签
     * @return 商品列表
     */
    List<Product> listByTag(String tag);
    
    /**
     * 统计商品总数
     * @return 商品总数
     */
    long count();
    
    /**
     * 查询销售排行前10的商品
     * @return 商品列表
     */
    List<Product> listTopSales();
}
