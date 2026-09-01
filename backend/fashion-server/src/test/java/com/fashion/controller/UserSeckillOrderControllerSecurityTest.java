package com.fashion.controller;

import com.fashion.controller.user.UserSeckillOrderController;
import com.fashion.dto.SeckillCancelResponse;
import com.fashion.result.Result;
import com.fashion.service.SeckillOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("B4 秒杀用户接口异常脱敏")
class UserSeckillOrderControllerSecurityTest {

    private static final String INTERNAL_MESSAGE = "SQLSyntaxErrorException: table seckill_order missing";
    private UserSeckillOrderController controller;
    private SeckillOrderService service;

    @BeforeEach
    void setUp() {
        controller = new UserSeckillOrderController();
        service = mock(SeckillOrderService.class);
        ReflectionTestUtils.setField(controller, "seckillOrderService", service);
    }

    @Test
    @DisplayName("详情异常不向公开响应返回 Mapper 内部消息")
    void detailDoesNotExposeMapperFailure() {
        when(service.getCurrentUserOrderByNumber("SEC-1"))
                .thenThrow(new RuntimeException(INTERNAL_MESSAGE));

        Result<?> result = controller.detail("SEC-1");

        assertEquals("获取订单详情失败", result.getMsg());
        assertFalse(result.getMsg().contains(INTERNAL_MESSAGE));
    }

    @Test
    @DisplayName("取消异常不向公开响应返回 Mapper 内部消息")
    void cancelDoesNotExposeMapperFailure() {
        when(service.cancelCurrentUserOrder("SEC-1"))
                .thenThrow(new RuntimeException(INTERNAL_MESSAGE));

        Result<?> result = controller.cancelOrder("SEC-1");

        assertEquals("取消订单失败", result.getMsg());
        assertFalse(result.getMsg().contains(INTERNAL_MESSAGE));
    }

    @Test
    @DisplayName("券列表异常不向公开响应返回 Mapper 内部消息")
    void couponsDoNotExposeMapperFailure() {
        when(service.getCurrentUserCoupons(null))
                .thenThrow(new RuntimeException(INTERNAL_MESSAGE));

        Result<?> result = controller.getCoupons(null);

        assertEquals("获取秒杀券列表失败", result.getMsg());
        assertFalse(result.getMsg().contains(INTERNAL_MESSAGE));
    }
}
