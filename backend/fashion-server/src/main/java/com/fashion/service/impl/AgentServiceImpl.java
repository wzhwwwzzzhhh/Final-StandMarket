package com.fashion.service.impl;

import com.fashion.dto.AgentChatRequest;
import com.fashion.dto.AgentChatResponse;
import com.fashion.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);
    private static final String PYTHON_AGENT_URL = "http://localhost:8000/chat";

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        try {
            AgentChatResponse response = restTemplate.postForObject(
                    PYTHON_AGENT_URL, request, AgentChatResponse.class);
            if (response == null) {
                log.warn("Python agent returned null response");
                return fallbackResponse("抱歉，AI 助手暂时无法回复，请稍后再试。");
            }
            return response;
        } catch (ResourceAccessException e) {
            log.error("Python agent connection timed out or refused: {}", e.getMessage());
            return fallbackResponse("AI 助手连接超时，请检查网络后重试。");
        } catch (RestClientException e) {
            log.error("Python agent call failed: {}", e.getMessage());
            return fallbackResponse("AI 助手暂时不可用，请稍后再试。");
        }
    }

    private AgentChatResponse fallbackResponse(String message) {
        AgentChatResponse resp = new AgentChatResponse();
        resp.setReply(message);
        resp.setSessionId("");
        resp.setProducts(null);
        return resp;
    }
}
