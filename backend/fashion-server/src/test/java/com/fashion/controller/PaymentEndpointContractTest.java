package com.fashion.controller;

import com.fashion.controller.admin.OrderController;
import com.fashion.controller.user.UserOrderController;
import com.fashion.controller.user.UserSeckillOrderController;
import com.fashion.entity.Orders;
import com.fashion.service.OrderService;
import com.fashion.service.PaymentService;
import com.fashion.service.SeckillOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("B1 支付入口契约")
class PaymentEndpointContractTest {

    @Test
    @DisplayName("普通订单随机模拟支付入口及 service 不再存在")
    void normalOrderSimulationIsRemoved() {
        assertFalse(hasPutMapping(UserOrderController.class, "/pay/{id}"));
        assertFalse(hasMethod(OrderService.class, "pay"));
        assertFalse(hasMethod(PaymentService.class, "processPayment"));
    }

    @Test
    @DisplayName("用户秒杀随机模拟支付入口及 service 不再存在")
    void seckillSimulationIsRemoved() {
        assertFalse(hasPostMapping(UserSeckillOrderController.class, "/pay/{orderNumber}"));
        assertFalse(hasMethod(SeckillOrderService.class, "completePayment"));
    }

    @Test
    @DisplayName("管理端普通订单人工确认支付入口及直写 service 不再存在")
    void adminManualPaymentIsRemoved() {
        assertFalse(hasPutMapping(OrderController.class, "/{id}/confirm-payment"));
        assertFalse(hasMethod(OrderService.class, "updatePaySuccess"));
    }

    @Test
    @DisplayName("管理端通用状态接口不再接收完整 Orders 实体")
    void adminStatusUpdateUsesDedicatedPayload() {
        Method method = Arrays.stream(OrderController.class.getDeclaredMethods())
                .filter(candidate -> hasPutMapping(candidate, "/{id}/status"))
                .findFirst()
                .orElse(null);

        assertNotNull(method);
        Class<?> requestBodyType = Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(RequestBody.class))
                .map(Parameter::getType)
                .findFirst()
                .orElse(null);
        assertNotNull(requestBodyType);
        assertNotEquals(Orders.class, requestBodyType);
    }

    private static boolean hasMethod(Class<?> type, String name) {
        return Arrays.stream(type.getMethods()).anyMatch(method -> method.getName().equals(name));
    }

    private static boolean hasPutMapping(Class<?> type, String path) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> hasPutMapping(method, path));
    }

    private static boolean hasPutMapping(Method method, String path) {
        PutMapping mapping = method.getAnnotation(PutMapping.class);
        return mapping != null && Arrays.asList(mapping.value()).contains(path);
    }

    private static boolean hasPostMapping(Class<?> type, String path) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            return mapping != null && Arrays.asList(mapping.value()).contains(path);
        });
    }
}
