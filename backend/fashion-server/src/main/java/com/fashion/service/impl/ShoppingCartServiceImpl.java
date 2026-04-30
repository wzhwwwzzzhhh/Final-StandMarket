package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.ShoppingCart;
import com.fashion.entity.Product;
import com.fashion.mapper.ShoppingCartMapper;
import com.fashion.service.ProductService;
import com.fashion.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.math.BigDecimal;

/**
 * 购物车Service实现类
 */
@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    
    @Autowired
    private ProductService productService;
    
    /**
     * 添加商品到购物车
     */
    @Transactional
    @Override
    public void add(ShoppingCart shoppingCart) {
        // 获取当前用户id
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        shoppingCart.setUserId(userId);
        
        // 检查商品库存
        Product product = productService.getById(shoppingCart.getProductId());
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        if (product.getStock() < shoppingCart.getNumber()) {
            throw new RuntimeException("商品库存不足");
        }
        
        // 查询购物车中是否已有该商品（相同商品id和规格）
        ShoppingCart existingCart = shoppingCartMapper.findByUserIdAndProductIdAndSkuInfo(shoppingCart);
        
        if (existingCart != null) {
            // 检查库存是否足够
            if (product.getStock() < existingCart.getNumber() + shoppingCart.getNumber()) {
                throw new RuntimeException("商品库存不足");
            }
            // 如果已存在，更新数量和金额
            existingCart.setNumber(existingCart.getNumber() + shoppingCart.getNumber());
            existingCart.setAmount(product.getPrice().multiply(new BigDecimal(existingCart.getNumber())));
            // 更新商品信息（确保名称和图片是最新的）
            existingCart.setName(product.getName());
            existingCart.setImage(product.getImage());
            shoppingCartMapper.updateNumberAndAmount(existingCart);
        } else {
            // 设置商品信息
            shoppingCart.setName(product.getName());
            shoppingCart.setImage(product.getImage());
            // 设置商品价格
            shoppingCart.setAmount(product.getPrice().multiply(new BigDecimal(shoppingCart.getNumber())));
            // 如果不存在，添加新商品
            shoppingCartMapper.insert(shoppingCart);
        }
    }
    
    /**
     * 获取用户购物车列表
     */
    @Override
    public List<ShoppingCart> list() {
        // 获取当前用户id
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        List<ShoppingCart> cartList = shoppingCartMapper.findByUserId(userId);
        // 同步价格和库存
        syncCartPrices();
        return cartList;
    }
    
    /**
     * 更新购物车商品数量
     */
    @Transactional
    @Override
    public void updateQuantity(ShoppingCart shoppingCart) {
        // 直接根据id查询购物车项
        ShoppingCart existingCart = shoppingCartMapper.findById(shoppingCart.getId());
        if (existingCart != null) {
            // 检查商品库存
            Product product = productService.getById(existingCart.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }
            if (product.getStock() < shoppingCart.getNumber()) {
                throw new RuntimeException("商品库存不足");
            }
            // 计算新的金额
            BigDecimal newAmount = product.getPrice().multiply(new BigDecimal(shoppingCart.getNumber()));
            existingCart.setNumber(shoppingCart.getNumber());
            existingCart.setAmount(newAmount);
            shoppingCartMapper.updateNumberAndAmount(existingCart);
        }
    }
    
    /**
     * 删除购物车商品
     */
    @Override
    public void delete(Long id) {
        // 直接删除，后续可根据实际登录状态添加权限验证
        shoppingCartMapper.deleteById(id);
    }
    
    /**
     * 清空购物车
     */
    @Override
    public void clear() {
        // 获取当前用户id
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        shoppingCartMapper.deleteByUserId(userId);
    }
    
    /**
     * 同步购物车商品价格和库存
     */
    @Override
    public void syncCartPrices() {
        // 获取当前用户id
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        List<ShoppingCart> cartList = shoppingCartMapper.findByUserId(userId);
        
        for (ShoppingCart cart : cartList) {
            Product product = productService.getById(cart.getProductId());
            if (product != null) {
                // 更新价格
                BigDecimal newAmount = product.getPrice().multiply(new BigDecimal(cart.getNumber()));
                if (!cart.getAmount().equals(newAmount)) {
                    cart.setAmount(newAmount);
                    shoppingCartMapper.updateNumberAndAmount(cart);
                }
            }
        }
    }
    
    /**
     * 检查购物车商品库存
     * @return 库存不足的商品列表
     */
    @Override
    public List<ShoppingCart> checkStock() {
        // 获取当前用户id
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        List<ShoppingCart> cartList = shoppingCartMapper.findByUserId(userId);
        List<ShoppingCart> outOfStockList = new java.util.ArrayList<>();
        
        for (ShoppingCart cart : cartList) {
            Product product = productService.getById(cart.getProductId());
            if (product == null || product.getStock() < cart.getNumber()) {
                outOfStockList.add(cart);
            }
        }
        
        return outOfStockList;
    }
    
    /**
     * 批量删除购物车商品
     */
    @Transactional
    @Override
    public void batchDelete(List<Long> ids) {
        // 调用mapper的批量删除方法
        shoppingCartMapper.batchDelete(ids);
    }
    
    /**
     * 结算购物车商品
     * @param ids 选中的购物车商品ID列表
     * @return 结算结果，包含商品列表、总价、优惠等信息
     */
    @Transactional
    @Override
    public com.fashion.vo.CartCheckoutVo checkout(List<Long> ids) {
        // 获取当前用户id
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        
        // 获取所有购物车商品
        List<ShoppingCart> allCartItems = shoppingCartMapper.findByUserId(userId);
        
        // 筛选出选中的商品
        List<ShoppingCart> selectedItems = new java.util.ArrayList<>();
        java.math.BigDecimal totalPrice = java.math.BigDecimal.ZERO;
        
        for (ShoppingCart item : allCartItems) {
            if (ids.contains(item.getId())) {
                selectedItems.add(item);
                totalPrice = totalPrice.add(item.getAmount());
            }
        }
        
        // 计算优惠（满100减10）
        java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
        if (totalPrice.compareTo(new java.math.BigDecimal(100)) >= 0) {
            int discountTimes = totalPrice.divide(new java.math.BigDecimal(100), 0, java.math.RoundingMode.FLOOR).intValue();
            discount = new java.math.BigDecimal(10).multiply(new java.math.BigDecimal(discountTimes));
        }
        
        // 计算最终价格
        java.math.BigDecimal finalPrice = totalPrice.subtract(discount);
        
        // 创建结算结果
        com.fashion.vo.CartCheckoutVo checkoutVo = new com.fashion.vo.CartCheckoutVo();
        checkoutVo.setItems(selectedItems);
        checkoutVo.setTotalPrice(totalPrice);
        checkoutVo.setDiscount(discount);
        checkoutVo.setFinalPrice(finalPrice);
        checkoutVo.setCartItemIds(ids);
        
        return checkoutVo;
    }
}