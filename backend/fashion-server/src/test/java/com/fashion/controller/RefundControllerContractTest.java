package com.fashion.controller;

import com.fashion.result.Result;
import com.fashion.service.RefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("B3 管理端退款响应契约")
class RefundControllerContractTest {

    @Test
    @DisplayName("审核同意返回等待外部退款而不是退款成功")
    void approveReportsWaitingForExternalRefund() {
        RefundService refundService = mock(RefundService.class);
        com.fashion.controller.admin.RefundController controller =
                new com.fashion.controller.admin.RefundController();
        ReflectionTestUtils.setField(controller, "refundService", refundService);
        Map<String, Object> request = new HashMap<>();
        request.put("id", 10L);
        request.put("opinion", "同意");

        Result<String> result = controller.approve(request);

        assertEquals(1, result.getCode());
        assertEquals("已同意，等待退款处理", result.getData());
    }
}
