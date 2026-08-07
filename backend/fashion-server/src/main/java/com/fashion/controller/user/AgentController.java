package com.fashion.controller.user;

import com.fashion.context.BaseContext;
import com.fashion.entity.Orders;
import com.fashion.result.Result;
import com.fashion.dto.AgentChatRequest;
import com.fashion.dto.AgentChatResponse;
import com.fashion.service.AgentService;
import com.fashion.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/agent")
public class AgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private OrderService orderService;

    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(@RequestBody AgentChatRequest request,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!StringUtils.hasText(request.getMessage())) {
            return Result.error("message cannot be empty");
        }
        // 以服务端登录态为准，防止前端伪造 userId 查询他人数据
        Long currentUserId = BaseContext.getUserId();
        if (currentUserId == null) {
            return Result.error("请先登录");
        }
        request.setUserId(currentUserId.intValue());
        // 透传用户 token，供 agent 调用订单等敏感接口时鉴权
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            request.setToken(authorization.substring(7));
        }
        AgentChatResponse response = agentService.chat(request);
        return Result.success(response);
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
}
