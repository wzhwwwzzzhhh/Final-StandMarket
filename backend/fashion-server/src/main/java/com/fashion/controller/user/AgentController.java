package com.fashion.controller.user;

import com.fashion.result.Result;
import com.fashion.dto.AgentChatRequest;
import com.fashion.dto.AgentChatResponse;
import com.fashion.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/agent")
public class AgentController {

    @Autowired
    private AgentService agentService;

    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        if (request.getUserId() == null || request.getUserId() < 1) {
            return Result.error("userId must be positive");
        }
        if (!StringUtils.hasText(request.getMessage())) {
            return Result.error("message cannot be empty");
        }
        AgentChatResponse response = agentService.chat(request);
        return Result.success(response);
    }
}
