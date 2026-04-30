package com.fashion.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.entity.Product;
import com.fashion.entity.PageResult;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fashion.dto.ProductQueryDTO;
import java.util.List;
import java.util.Map;

@Service
public class ProductServiceImpl implements ProductService {
    
    @Autowired
    private ProductMapper productMapper;


    /**
     * 分页查询商品（使用DTO）
     * @param query 查询参数DTO
     * @return 分页后的商品列表
     */
    @Override
    public PageResult<Product> pageProducts(ProductQueryDTO query) {
        // 开始分页
        PageHelper.startPage(query.getPage(), query.getPageSize());
        // 执行查询
        List<Product> products = productMapper.listProductsByCondition(
                query.getKeyword(),
                query.getCategoryId(),
                query.getTag(),
                query.getSortBy(),
                query.getIsSale()
        );
        // 包装成PageInfo
        PageInfo<Product> pageInfo = new PageInfo<>(products);
        // 构造PageResult返回
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }
    
    /**
     * 根据ID查询商品
     * @param id 商品ID
     * @return 商品
     */
    @Override
    public Product getById(Long id) {
        return productMapper.getById(id);
    }
    
    /**
     * 新增商品
     * @param product 商品信息
     * @return 是否成功
     */
    @Override
    public boolean save(Product product) {
        return productMapper.save(product) > 0;
    }
    
    /**
     * 更新商品
     * @param product 商品信息
     * @return 是否成功
     */
    @Override
    public boolean update(Product product) {
        return productMapper.update(product) > 0;
    }
    
    /**
     * 删除商品
     * @param id 商品ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Long id) {
        return productMapper.deleteById(id) > 0;
    }
    

    @Override
    public long count() {
        return productMapper.count();
    }
    
    @Override
    public List<Product> listTopSales() {
        return productMapper.listTopSales();
    }
}
