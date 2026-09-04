package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.entity.AddressBook;
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
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B4 下单地址归属")
class OrderAddressOwnershipTest {

    private OrderServiceImpl service;
    private AddressBookMapper addressMapper;
    private OrderMapper orderMapper;
    private ProductMapper productMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        BaseContext.setUserId(7L);
        addressMapper = mock(AddressBookMapper.class);
        orderMapper = mock(OrderMapper.class);
        OrderDetailMapper orderDetailMapper = mock(OrderDetailMapper.class);
        ShoppingCartMapper shoppingCartMapper = mock(ShoppingCartMapper.class);
        productMapper = mock(ProductMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);

        ShoppingCart cart = new ShoppingCart();
        cart.setId(11L);
        cart.setUserId(7L);
        cart.setProductId(100L);
        cart.setName("coat");
        cart.setNumber(1);
        when(shoppingCartMapper.findByIdsAndUserId(7L, Collections.singletonList(11L)))
                .thenReturn(Collections.singletonList(cart));

        Product product = new Product();
        product.setId(100L);
        product.setName("coat");
        product.setPrice(new BigDecimal("88.00"));
        product.setStock(3);
        product.setStatus(1);
        when(productMapper.getById(100L)).thenReturn(product);
        when(productMapper.deductStock(100L, 1)).thenReturn(1);
        when(orderMapper.insert(any())).thenReturn(1);
        when(orderDetailMapper.batchInsert(any())).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "addressBookMapper", addressMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", orderDetailMapper);
        ReflectionTestUtils.setField(service, "shoppingCartMapper", shoppingCartMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "couponService", mock(CouponService.class));
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("他人或不存在地址在任何库存和订单写入前被拒绝")
    void rejectsUnownedAddressBeforeBusinessWrites() {
        OrderCreateDTO request = request(9L);
        when(addressMapper.getByIdAndUserId(9L, 7L)).thenReturn(null);

        assertThrows(PublicBusinessException.class, () -> service.create(request));

        verify(productMapper, never()).deductStock(any(), any());
        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("本人地址被复制为订单快照")
    void copiesOnlyOwnedAddressSnapshot() {
        AddressBook address = ownedAddress();
        when(addressMapper.getByIdAndUserId(9L, 7L)).thenReturn(address);

        Orders created = service.create(request(9L));

        assertEquals(9L, created.getAddressBookId());
        assertEquals("张三", created.getConsignee());
        assertEquals("13800000000", created.getPhone());
        assertEquals("浙江杭州西湖文三路", created.getAddress());
        verify(addressMapper).getByIdAndUserId(9L, 7L);
    }

    @Test
    @DisplayName("空地址引用保持现有可选语义")
    void nullAddressPreservesExistingSemantics() {
        Orders created = service.create(request(null));

        assertNull(created.getAddressBookId());
        verify(addressMapper, never()).getByIdAndUserId(any(), any());
    }

    private static OrderCreateDTO request(Long addressId) {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(Collections.singletonList(11L));
        request.setAddressId(addressId);
        return request;
    }

    private static AddressBook ownedAddress() {
        AddressBook address = new AddressBook();
        address.setId(9L);
        address.setUserId(7L);
        address.setConsignee("张三");
        address.setPhone("13800000000");
        address.setProvinceName("浙江");
        address.setCityName("杭州");
        address.setDistrictName("西湖");
        address.setDetail("文三路");
        return address;
    }
}
