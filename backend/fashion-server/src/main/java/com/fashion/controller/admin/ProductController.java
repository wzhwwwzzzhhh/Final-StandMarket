package com.fashion.controller.admin;

import com.fashion.entity.Product;
import com.fashion.entity.PageResult;
import com.fashion.dto.ProductSaveDTO;
import com.fashion.dto.ProductUpdateDTO;
import com.fashion.result.Result;
import com.fashion.service.ProductService;
import com.fashion.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.fashion.dto.ProductQueryDTO;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 商品管理
 */
@RestController
@RequestMapping("/admin/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheClient cacheClient;

    /**
     * 新增商品
     */
    @PostMapping
    public Result<String> save(@RequestBody ProductSaveDTO productSaveDTO) {
        // 转换为Product实体
        Product product = new Product();
        product.setName(productSaveDTO.getName());
        product.setDescription(productSaveDTO.getDescription());
        product.setPrice(productSaveDTO.getPrice());
        product.setStock(productSaveDTO.getStock());
        product.setImage(productSaveDTO.getImage());
        product.setCategoryId(productSaveDTO.getCategoryId());
        product.setTag(productSaveDTO.getTag());
        product.setStatus(productSaveDTO.getStatus());

        productService.save(product);
        cacheClient.delete("productPage:*");
        return Result.success();
    }

    /**
     * 分页查询
     */
    @GetMapping
    public Result<PageResult<Product>> page(ProductQueryDTO query) {
        // 调用Service层的分页查询方法
        PageResult<Product> pageResult = productService.pageProducts(query);

        return Result.success(pageResult);
    }

    /**
     * 根据id查询商品
     */
    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        Product product = productService.getById(id);
        if(product == null){
            return Result.error("商品不存在");
        }
        return Result.success(product);
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        boolean flag = productService.removeById(id);
        if(!flag){
            return Result.error("删除失败");
        }
        cacheClient.delete("productPage:*");
        return Result.success();
    }

    /**
     * 修改商品
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @RequestBody ProductUpdateDTO productUpdateDTO) {
        if(id == null){
            return Result.error("id不能为空");
        }
        // 转换为Product实体
        Product product = new Product();
        product.setId(id);
        product.setName(productUpdateDTO.getName());
        product.setDescription(productUpdateDTO.getDescription());
        product.setPrice(productUpdateDTO.getPrice());
        product.setStock(productUpdateDTO.getStock());
        product.setImage(productUpdateDTO.getImage());
        product.setCategoryId(productUpdateDTO.getCategoryId());
        product.setTag(productUpdateDTO.getTag());
        product.setStatus(productUpdateDTO.getStatus());

        boolean flag = productService.update(product);
        if(!flag){
            return Result.error("修改失败");
        }
        cacheClient.delete("productPage:*");
        return Result.success();
    }

}
