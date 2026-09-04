package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.entity.Orders;
import com.fashion.entity.Product;
import com.fashion.entity.ShoppingCart;
import com.fashion.exception.PublicBusinessException;
import com.fashion.mapper.AddressBookMapper;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.ShoppingCartMapper;
import com.fashion.service.CouponService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B2 普通订单创建与库存边界")
class OrderCreationInventoryTest {

    private OrderServiceImpl service;
    private OrderMapper orderMapper;
    private OrderDetailMapper orderDetailMapper;
    private ShoppingCartMapper shoppingCartMapper;
    private ProductMapper productMapper;
    private ValueOperations<String, String> valueOperations;
    private ShoppingCart cart;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        BaseContext.setUserId(7L);

        orderMapper = mock(OrderMapper.class);
        orderDetailMapper = mock(OrderDetailMapper.class);
        shoppingCartMapper = mock(ShoppingCartMapper.class);
        productMapper = mock(ProductMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        cart = new ShoppingCart();
        cart.setId(11L);
        cart.setUserId(7L);
        cart.setProductId(100L);
        cart.setName("coat");
        cart.setNumber(2);
        when(shoppingCartMapper.findById(11L)).thenReturn(cart);
        when(shoppingCartMapper.findByIdsAndUserId(eq(7L), eq(Collections.singletonList(11L))))
                .thenReturn(Collections.singletonList(cart));

        Product product = new Product();
        product.setId(100L);
        product.setName("coat");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(10);
        product.setStatus(1);
        when(productMapper.getById(100L)).thenReturn(product);
        when(productMapper.deductStock(100L, 2)).thenReturn(1);
        when(orderMapper.insert(any())).thenReturn(1);
        when(orderDetailMapper.batchInsert(any())).thenAnswer(invocation ->
                ((java.util.List<?>) invocation.getArgument(0)).size());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", orderDetailMapper);
        ReflectionTestUtils.setField(service, "addressBookMapper", mock(AddressBookMapper.class));
        ReflectionTestUtils.setField(service, "shoppingCartMapper", shoppingCartMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "couponService", mock(CouponService.class));
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("普通订单忽略客户端金额和秒杀标识")
    void ordinaryOrderIgnoresClientAmountAndSeckillIdentifiers() {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(Collections.singletonList(11L));
        request.setAmount(new BigDecimal("0.01"));
        request.setActivityId(99L);
        request.setCouponId(88L);

        Orders created = service.create(request);

        assertEquals(new BigDecimal("20.00"), created.getOriginalPrice());
        assertEquals(new BigDecimal("20.00"), created.getAmount());
        assertEquals(0, created.getIsSeckill());
        assertNull(created.getSeckillActivityId());
        assertNull(created.getSeckillCouponId());
        assertNull(created.getSeckillPrice());
        verify(orderMapper).insert(created);
        verify(orderDetailMapper).batchInsert(any());
    }

    @Test
    @DisplayName("重复购物车项不能被重复计价和扣库存")
    void rejectsDuplicateCartItemIdentifiers() {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(java.util.Arrays.asList(11L, 11L));

        assertThrows(PublicBusinessException.class, () -> service.create(request));

        verify(orderMapper, never()).insert(any());
        verify(orderDetailMapper, never()).batchInsert(any());
    }

    @Test
    @DisplayName("条件扣库存失败时订单不能落库")
    void rejectsOrderWhenConditionalStockDeductionFails() {
        when(productMapper.deductStock(100L, 2)).thenReturn(0);
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(Collections.singletonList(11L));

        assertThrows(PublicBusinessException.class, () -> service.create(request));

        verify(productMapper).deductStock(100L, 2);
        verify(orderMapper, never()).insert(any());
        verify(orderDetailMapper, never()).batchInsert(any());
    }

    @Test
    @DisplayName("同一商品的多个购物车项必须聚合后只扣一次库存")
    void aggregatesQuantitiesByProductBeforeDeductingStock() {
        ShoppingCart secondCart = new ShoppingCart();
        secondCart.setId(12L);
        secondCart.setUserId(7L);
        secondCart.setProductId(100L);
        secondCart.setName("coat");
        secondCart.setNumber(3);
        when(shoppingCartMapper.findById(12L)).thenReturn(secondCart);
        when(shoppingCartMapper.findByIdsAndUserId(7L, Arrays.asList(11L, 12L)))
                .thenReturn(Arrays.asList(cart, secondCart));
        when(productMapper.deductStock(100L, 5)).thenReturn(1);

        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(Arrays.asList(11L, 12L));

        service.create(request);

        verify(productMapper).deductStock(100L, 5);
        verify(productMapper, never()).deductStock(100L, 2);
        verify(productMapper, never()).deductStock(100L, 3);
    }

    @Test
    @DisplayName("非正购物车数量必须在库存写入前被拒绝")
    void rejectsNonPositiveQuantityBeforeInventoryMutation() {
        cart.setNumber(0);
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(Collections.singletonList(11L));

        assertThrows(PublicBusinessException.class, () -> service.create(request));

        verify(productMapper, never()).deductStock(any(), any());
        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("超过一百个购物车项必须在生成订单号前被拒绝")
    void rejectsOversizedCartSelectionBeforeRedisMutation() {
        List<Long> cartItemIds = LongStream.rangeClosed(1, 101)
                .boxed()
                .collect(Collectors.toList());
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(cartItemIds);

        assertThrows(PublicBusinessException.class, () -> service.create(request));

        verify(valueOperations, never()).increment(anyString());
        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("非正购物车项标识必须在生成订单号前被拒绝")
    void rejectsNonPositiveCartItemIdentifierBeforeRedisMutation() {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(Collections.singletonList(0L));

        assertThrows(PublicBusinessException.class, () -> service.create(request));

        verify(valueOperations, never()).increment(anyString());
        verify(orderMapper, never()).insert(any());
    }
}
