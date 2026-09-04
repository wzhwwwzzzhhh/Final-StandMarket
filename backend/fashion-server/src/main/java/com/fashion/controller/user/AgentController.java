package com.fashion.controller.user;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fashion.context.BaseContext;
import com.fashion.entity.Orders;
import com.fashion.result.Result;
import com.fashion.dto.AgentChatRequest;
import com.fashion.dto.AgentChatResponse;
import com.fashion.dto.AgentInternalChatRequest;
import com.fashion.service.AgentService;
import com.fashion.service.OrderService;
import com.fashion.util.AgentSessionIdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/agent")
public class AgentController {

    private final AgentService agentService;
    private final OrderService orderService;
    private final AgentSessionIdGenerator sessionIdGenerator;

    public AgentController(AgentService agentService, OrderService orderService,
                           AgentSessionIdGenerator sessionIdGenerator) {
        this.agentService = agentService;
        this.orderService = orderService;
        this.sessionIdGenerator = sessionIdGenerator;
    }

    @PostMapping("/chat")
    public ResponseEntity<Result<AgentChatResponse>> chat(
            @RequestBody(required = false) AgentChatRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (request == null || !StringUtils.hasText(request.getMessage())
                || request.getMessage().trim().length() > 2000) {
            return ResponseEntity.unprocessableEntity().body(Result.error("INVALID_MESSAGE"));
        }
        Long currentUserId = BaseContext.getUserId();
        if (currentUserId == null || !StringUtils.hasText(authorization)
                || !authorization.matches("^Bearer [^\\s]+$")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error("UNAUTHORIZED"));
        }

        String sessionId = request.getSessionId();
        if (request.sessionIdWasProvided()) {
            if (!StringUtils.hasText(sessionId) || !sessionIdGenerator.isValid(sessionId)) {
                return ResponseEntity.unprocessableEntity().body(Result.error("INVALID_SESSION_ID"));
            }
        } else {
            sessionId = sessionIdGenerator.generate();
        }

        AgentInternalChatRequest internalRequest = new AgentInternalChatRequest();
        internalRequest.setUserId(currentUserId);
        internalRequest.setSessionId(sessionId);
        internalRequest.setMessage(request.getMessage().trim());
        internalRequest.setUserAuthorization(authorization);
        AgentChatResponse response = agentService.chat(internalRequest);
        return ResponseEntity.ok(Result.success(response));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadableChatRequest(HttpMessageNotReadableException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof JsonMappingException) {
                for (JsonMappingException.Reference reference : ((JsonMappingException) cause).getPath()) {
                    if ("sessionId".equals(reference.getFieldName())) {
                        return ResponseEntity.unprocessableEntity().body(Result.error("INVALID_SESSION_ID"));
                    }
                }
            }
            cause = cause.getCause();
        }
        return ResponseEntity.unprocessableEntity().body(Result.error("INVALID_MESSAGE"));
    }

    /**
     * agent 专用订单查询：走用户登录态（BaseContext），从 JWT 取 userId，忽略前端/AI 传入的 userId
     */
    @GetMapping("/order/list")
    public Result<List<Orders>> agentOrderList() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        List<Orders> orders = orderService.listUserOrders(null);
        return Result.success(orders);
    }

    /**
     * agent 专用物流查询：仅允许本人查询，返回物流信息
     */
    @GetMapping("/tracking/{orderId}")
    public Result<Map<String, Object>> agentTracking(@PathVariable Long orderId) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        Orders order = orderService.getCurrentUserOrderById(orderId);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (order.getTrackingNumber() == null || order.getTrackingNumber().isEmpty()) {
            return Result.error("暂无物流信息");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("number", order.getNumber());
        result.put("trackingCompany", order.getTrackingCompany());
        result.put("trackingNumber", order.getTrackingNumber());
        result.put("deliveryTime", order.getDeliveryTime());
        result.put("estimatedDeliveryTime", order.getEstimatedDeliveryTime());
        result.put("status", order.getStatus());
        return Result.success(result);
    }
}
