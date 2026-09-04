package com.fashion.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.entity.Product;
import com.fashion.entity.PageResult;
import com.fashion.mapper.ProductMapper;
import com.fashion.product.ProductCatalogMutationCoordinator;
import com.fashion.product.ProductItemState;
import com.fashion.product.ProductMutationClassifier;
import com.fashion.product.ProductMutationKind;
import com.fashion.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fashion.dto.ProductQueryDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductCatalogMutationCoordinator catalogCoordinator;

    public ProductServiceImpl(ProductMapper productMapper,
                              ProductCatalogMutationCoordinator catalogCoordinator) {
        this.productMapper = productMapper;
        this.catalogCoordinator = catalogCoordinator;
    }


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

    @Override
    public Product getByIdIncludingInactive(Long id) {
        return productMapper.getByIdIncludingInactive(id);
    }
    
    /**
     * 新增商品
     * @param product 商品信息
     * @return 是否成功
     */
    @Override
    @Transactional
    public boolean save(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is required");
        }
        product.setSales(product.getSales() == null ? 0 : product.getSales());
        LocalDateTime now = LocalDateTime.now();
        if (product.getCreateTime() == null) {
            product.setCreateTime(now);
        }
        if (product.getUpdateTime() == null) {
            product.setUpdateTime(now);
        }
        if (productMapper.save(product) != 1 || product.getId() == null) {
            return false;
        }
        Product committed = requireProjectionSnapshot(product.getId());
        if (!Objects.equals(product.getImage(), committed.getImage())
                || normalizeSales(committed.getSales()) != product.getSales()) {
            throw new IllegalStateException("persisted product projection differs from requested facts");
        }
        catalogCoordinator.record(committed, stateOf(committed));
        return true;
    }
    
    /**
     * 更新商品
     * @param product 商品信息
     * @return 是否成功
     */
    @Override
    @Transactional
    public boolean update(Product product) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("product id is required");
        }
        Product existing = productMapper.getByIdForUpdate(product.getId());
        if (existing == null) {
            return false;
        }
        ProductMutationKind kind = ProductMutationClassifier.classify(existing, product);
        if (!kind.changesAnything()) {
            return true;
        }
        if (productMapper.update(product) != 1) {
            return false;
        }
        if (kind.changesCatalog()) {
            Product committed = requireProjectionSnapshot(product.getId());
            catalogCoordinator.record(committed, stateOf(committed));
        }
        return true;
    }
    
    /**
     * 删除商品
     * @param id 商品ID
     * @return 是否成功
     */
    @Override
    @Transactional
    public boolean removeById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("product id is required");
        }
        Product existing = productMapper.getByIdForUpdate(id);
        if (existing == null) {
            return false;
        }
        if (productMapper.deleteById(id) != 1) {
            return false;
        }
        existing.setSales(normalizeSales(existing.getSales()));
        catalogCoordinator.record(existing, ProductItemState.DELETED);
        return true;
    }

    private Product requireProjectionSnapshot(Long id) {
        Product snapshot = productMapper.getByIdIncludingInactive(id);
        if (snapshot == null) {
            throw new IllegalStateException("persisted product projection snapshot is missing");
        }
        snapshot.setSales(normalizeSales(snapshot.getSales()));
        return snapshot;
    }

    private int normalizeSales(Integer sales) {
        if (sales == null) {
            return 0;
        }
        if (sales < 0) {
            throw new IllegalStateException("product sales cannot be negative");
        }
        return sales;
    }

    private ProductItemState stateOf(Product product) {
        return Integer.valueOf(1).equals(product.getStatus())
                ? ProductItemState.ACTIVE : ProductItemState.INACTIVE;
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
