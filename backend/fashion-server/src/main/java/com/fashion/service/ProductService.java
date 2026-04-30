package com.fashion.service;

import com.fashion.dto.ProductQueryDTO;
import com.fashion.entity.Product;
import com.fashion.entity.PageResult;
import java.util.List;
import java.util.Map;

public interface ProductService {

    /**
     * 分页查询商品（支持分类、关键词、排序）
     * @param
     * @return 分页后的商品列表
     */
    PageResult<Product> pageProducts(ProductQueryDTO query);
    
    /**
     * 根据ID查询商品
     * @param id 商品ID
     * @return 商品
     */
    Product getById(Long id);
    
    /**
     * 新增商品
     * @param product 商品信息
     * @return 是否成功
     */
    boolean save(Product product);
    
    /**
     * 更新商品
     * @param product 商品信息
     * @return 是否成功
     */
    boolean update(Product product);
    
    /**
     * 删除商品
     * @param id 商品ID
     * @return 是否成功
     */
    boolean removeById(Long id);

    /**
     * 统计商品总数
     * @return 商品总数
     */
    long count();
    
    /**
     * 查询销量前10的商品
     * @return 商品列表
     */
    List<Product> listTopSales();
}
