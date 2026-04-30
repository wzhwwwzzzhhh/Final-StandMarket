package com.fashion.controller.user;

import com.fashion.entity.ShoppingCart;
import com.fashion.result.Result;
import com.fashion.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户购物车Controller
 */
@RestController
@RequestMapping("/user/shopping-cart")
public class UserShoppingCartController {
    
    @Autowired
    private ShoppingCartService shoppingCartService;
    
    /**
     * 添加商品到购物车
     */
    @PostMapping("/add")
    public Result<String> add(@RequestParam Long productId, @RequestParam Integer number, @RequestParam(required = false) String skuInfo) {
        try {
            // 转换为ShoppingCart实体
            ShoppingCart shoppingCart = new ShoppingCart();
            shoppingCart.setProductId(productId);
            shoppingCart.setNumber(number);
            shoppingCart.setSkuInfo(skuInfo);
            
            shoppingCartService.add(shoppingCart);
            return Result.success("添加成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 获取购物车列表
     */
    @GetMapping("/list")
    public Result<List<ShoppingCart>> list() {
        List<ShoppingCart> shoppingCartList = shoppingCartService.list();
        return Result.success(shoppingCartList);
    }
    
    /**
     * 更新购物车商品数量
     */
    @PutMapping("/update")
    public Result<String> update(@RequestParam Long productId, @RequestParam Integer number) {
        try {
            // 转换为ShoppingCart实体
            ShoppingCart shoppingCart = new ShoppingCart();
            shoppingCart.setProductId(productId);
            shoppingCart.setNumber(number);
            
            shoppingCartService.updateQuantity(shoppingCart);
            return Result.success("更新成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 删除购物车商品
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        shoppingCartService.delete(id);
        return Result.success("删除成功");
    }
    
    /**
     * 清空购物车
     */
    @DeleteMapping("/clear")
    public Result<String> clear() {
        shoppingCartService.clear();
        return Result.success("清空成功");
    }
    
    /**
     * 检查购物车商品库存
     */
    @GetMapping("/check-stock")
    public Result<List<ShoppingCart>> checkStock() {
        List<ShoppingCart> outOfStockList = shoppingCartService.checkStock();
        return Result.success(outOfStockList);
    }
    
    /**
     * 同步购物车商品价格
     */
    @PostMapping("/sync-prices")
    public Result<String> syncPrices() {
        shoppingCartService.syncCartPrices();
        return Result.success("价格同步成功");
    }
    
    /**
     * 批量删除购物车商品
     */
    @DeleteMapping("/batch-delete")
    public Result<String> batchDelete(@RequestBody List<Long> ids) {
        shoppingCartService.batchDelete(ids);
        return Result.success("批量删除成功");
    }
    
    /**
     * 结算购物车商品
     */
    @PostMapping("/checkout")
    public Result<com.fashion.vo.CartCheckoutVo> checkout(@RequestBody List<Long> ids) {
        com.fashion.vo.CartCheckoutVo checkoutVo = shoppingCartService.checkout(ids);
        return Result.success(checkoutVo);
    }
}