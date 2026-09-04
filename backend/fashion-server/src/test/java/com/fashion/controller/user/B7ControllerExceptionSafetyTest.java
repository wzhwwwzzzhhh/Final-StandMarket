package com.fashion.controller.user;

import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.exception.BaseException;
import com.fashion.exception.PublicBusinessException;
import com.fashion.result.Result;
import com.fashion.service.CouponService;
import com.fashion.service.OrderService;
import com.fashion.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.fashion.exception.PublicBusinessException.Code.CART_EMPTY;
import static com.fashion.exception.PublicBusinessException.of;

@DisplayName("B7 用户端异常响应脱敏")
class B7ControllerExceptionSafetyTest {

    @Test
    @DisplayName("可公开异常不提供任意字符串构造入口")
    void publicBusinessExceptionHasAClosedMessageCatalog() {
        assertThrows(NoSuchMethodException.class,
                () -> PublicBusinessException.class.getConstructor(String.class));
    }

    @Test
    @DisplayName("目录内业务文案仍可安全返回")
    void cataloguedBusinessMessageIsReturned() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.create(any())).thenThrow(of(CART_EMPTY));
        UserOrderController controller = new UserOrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);

        Result<?> result = controller.create(new OrderCreateDTO());

        assertEquals("购物车为空", result.getMsg());
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("下单基础设施异常不能泄露连接信息")
    void orderCreationInfrastructureFailureIsSanitized() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.create(any())).thenThrow(
                new DataAccessResourceFailureException("jdbc:mysql://db/prod?password=secret"));
        UserOrderController controller = new UserOrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);

        Result<?> result = controller.create(new OrderCreateDTO());

        assertEquals(0, result.getCode());
        assertEquals("下单失败，请稍后重试", result.getMsg());
        assertFalse(result.getMsg().contains("password"));
    }

    @Test
    @DisplayName("领券基础设施异常不能泄露连接信息")
    void couponClaimInfrastructureFailureIsSanitized() {
        BaseContext.setUserId(7L);
        CouponService couponService = mock(CouponService.class);
        doThrow(new DataAccessResourceFailureException("redis://cache:6379 password=secret"))
                .when(couponService).claim(7L, 9L);
        UserCouponController controller = new UserCouponController();
        ReflectionTestUtils.setField(controller, "couponService", couponService);

        Result<?> result = controller.claim(9L);

        assertEquals(0, result.getCode());
        assertEquals("领取优惠券失败，请稍后重试", result.getMsg());
        assertFalse(result.getMsg().contains("password"));
    }

    @Test
    @DisplayName("未分类下单异常同样不能直接回显")
    void unclassifiedOrderFailureIsSanitized() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.create(any())).thenThrow(new RuntimeException("internal secret marker"));
        UserOrderController controller = new UserOrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);

        Result<?> result = controller.create(new OrderCreateDTO());

        assertEquals("下单失败，请稍后重试", result.getMsg());
        assertFalse(result.getMsg().contains("secret"));
    }

    @Test
    @DisplayName("未分类领券异常同样不能直接回显")
    void unclassifiedCouponFailureIsSanitized() {
        BaseContext.setUserId(7L);
        CouponService couponService = mock(CouponService.class);
        doThrow(new RuntimeException("internal secret marker")).when(couponService).claim(7L, 9L);
        UserCouponController controller = new UserCouponController();
        ReflectionTestUtils.setField(controller, "couponService", couponService);

        Result<?> result = controller.claim(9L);

        assertEquals("领取优惠券失败，请稍后重试", result.getMsg());
        assertFalse(result.getMsg().contains("secret"));
    }

    @Test
    @DisplayName("公开评价查询基础设施异常不能泄露底层信息")
    void publicReviewInfrastructureFailureIsSanitized() {
        ReviewService reviewService = mock(ReviewService.class);
        when(reviewService.getProductReviews(20L, 1, 10, null)).thenThrow(
                new DataAccessResourceFailureException("jdbc:mysql://db/prod?password=secret"));
        ReviewController controller = new ReviewController();
        ReflectionTestUtils.setField(controller, "reviewService", reviewService);

        Result<?> result = controller.list(20L, 1, 10, null);

        assertEquals(0, result.getCode());
        assertEquals("获取评价失败，请稍后重试", result.getMsg());
        assertFalse(result.getMsg().contains("password"));
    }

    @Test
    @DisplayName("通用业务异常类型也不能成为任意消息回显通道")
    void genericBaseExceptionIsSanitized() {
        BaseContext.setUserId(7L);
        CouponService couponService = mock(CouponService.class);
        doThrow(new BaseException("constraint uk_user_coupon password=secret"))
                .when(couponService).claim(7L, 9L);
        UserCouponController controller = new UserCouponController();
        ReflectionTestUtils.setField(controller, "couponService", couponService);

        Result<?> result = controller.claim(9L);

        assertEquals("领取优惠券失败，请稍后重试", result.getMsg());
        assertFalse(result.getMsg().contains("password"));
    }

    @Test
    @DisplayName("任意参数异常不能由下单接口原样回显")
    void arbitraryIllegalArgumentExceptionIsSanitized() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.create(any())).thenThrow(
                new IllegalArgumentException("jdbc:mysql://db/prod?password=secret"));
        UserOrderController controller = new UserOrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);

        Result<?> result = controller.create(new OrderCreateDTO());

        assertEquals("下单失败，请稍后重试", result.getMsg());
        assertFalse(result.getMsg().contains("password"));
    }

    @Test
    @DisplayName("通用评价异常不能绕过公开响应脱敏")
    void genericReviewBaseExceptionIsSanitized() {
        ReviewService reviewService = mock(ReviewService.class);
        when(reviewService.getProductReviews(20L, 1, 10, null)).thenThrow(
                new BaseException("select * from user password=secret"));
        ReviewController controller = new ReviewController();
        ReflectionTestUtils.setField(controller, "reviewService", reviewService);

        Result<?> result = controller.list(20L, 1, 10, null);

        assertEquals("获取评价失败，请稍后重试", result.getMsg());
        assertFalse(result.getMsg().contains("password"));
    }
}
