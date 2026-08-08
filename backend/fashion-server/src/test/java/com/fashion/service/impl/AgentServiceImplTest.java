package com.fashion.service.impl;

import com.fashion.dto.AgentChatRequest;
import com.fashion.dto.AgentChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentService 单元测试")
class AgentServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AgentServiceImpl agentService;

    private AgentChatRequest request;

    @BeforeEach
    void setUp() {
        request = new AgentChatRequest();
        request.setUserId(1);
        request.setSessionId("test-session");
        request.setMessage("帮我找一件连衣裙");
    }

    @Test
    @DisplayName("测试成功调用 Python Agent 服务")
    void testChatSuccess() {
        AgentChatResponse mockResponse = new AgentChatResponse();
        mockResponse.setReply("为您推荐以下连衣裙...");
        mockResponse.setSessionId("test-session");

        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(AgentChatResponse.class)
        )).thenReturn(mockResponse);

        AgentChatResponse result = agentService.chat(request);

        assertNotNull(result);
        assertEquals("为您推荐以下连衣裙...", result.getReply());
        assertEquals("test-session", result.getSessionId());
        verify(restTemplate, times(1)).postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(AgentChatResponse.class)
        );
    }

    @Test
    @DisplayName("测试 Python Agent 服务返回非 2xx 状态码")
    void testChatNon2xxStatus() {
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(AgentChatResponse.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        AgentChatResponse result = agentService.chat(request);

        assertNotNull(result);
        assertEquals("AI 助手暂时不可用，请稍后再试。", result.getReply());
        assertEquals("", result.getSessionId());
    }

    @Test
    @DisplayName("测试 Python Agent 服务返回 null body")
    void testChatNullBody() {
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(AgentChatResponse.class)
        )).thenReturn(null);

        AgentChatResponse result = agentService.chat(request);

        assertNotNull(result);
        assertEquals("抱歉，AI 助手暂时无法回复，请稍后再试。", result.getReply());
        assertEquals("", result.getSessionId());
    }

    @Test
    @DisplayName("测试 Python Agent 服务连接超时")
    void testChatTimeout() {
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(AgentChatResponse.class)
        )).thenThrow(new ResourceAccessException("Connection timed out"));

        AgentChatResponse result = agentService.chat(request);

        assertNotNull(result);
        assertEquals("AI 助手连接超时，请检查网络后重试。", result.getReply());
        assertEquals("", result.getSessionId());
    }

    @Test
    @DisplayName("测试 Python Agent 服务连接被拒绝")
    void testChatConnectionRefused() {
        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(AgentChatResponse.class)
        )).thenThrow(new ResourceAccessException("Connection refused: connect"));

        AgentChatResponse result = agentService.chat(request);

        assertNotNull(result);
        assertEquals("AI 助手连接超时，请检查网络后重试。", result.getReply());
        assertEquals("", result.getSessionId());
    }

    @Test
    @DisplayName("测试请求不包含 sessionId")
    void testChatWithoutSessionId() {
        request.setSessionId(null);

        AgentChatResponse mockResponse = new AgentChatResponse();
        mockResponse.setReply("您好，有什么可以帮您的？");
        mockResponse.setSessionId("new-session-123");

        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(AgentChatResponse.class)
        )).thenReturn(mockResponse);

        AgentChatResponse result = agentService.chat(request);

        assertNotNull(result);
        assertEquals("new-session-123", result.getSessionId());
    }
}
