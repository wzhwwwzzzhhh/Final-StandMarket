package com.fashion.service.impl;

import com.fashion.config.AgentProperties;
import com.fashion.dto.AgentChatResponse;
import com.fashion.dto.AgentInternalChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private AgentServiceImpl agentService;

    @Mock
    private AgentProperties properties;

    private AgentInternalChatRequest request;

    @BeforeEach
    void setUp() {
        when(properties.getChatUrl()).thenReturn("http://127.0.0.1:8000/chat");
        when(properties.getInternalToken()).thenReturn("0123456789abcdef0123456789abcdef");
        agentService = new AgentServiceImpl(restTemplate, properties);
        request = new AgentInternalChatRequest();
        request.setUserId(1L);
        request.setSessionId("abcdefghijklmnopqrstuv");
        request.setMessage("帮我找一件连衣裙");
        request.setUserAuthorization("Bearer delegated-user-token");
    }

    @Test
    @DisplayName("测试成功调用 Python Agent 服务")
    void testChatSuccess() {
        AgentChatResponse mockResponse = new AgentChatResponse();
        mockResponse.setReply("为您推荐以下连衣裙...");
        mockResponse.setSessionId(request.getSessionId());

        when(restTemplate.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(AgentChatResponse.class)
        )).thenReturn(mockResponse);

        AgentChatResponse result = agentService.chat(request);

        assertNotNull(result);
        assertEquals("为您推荐以下连衣裙...", result.getReply());
        assertEquals(request.getSessionId(), result.getSessionId());
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
        assertEquals(request.getSessionId(), result.getSessionId());
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
        assertEquals("AI 助手暂时不可用，请稍后再试。", result.getReply());
        assertEquals(request.getSessionId(), result.getSessionId());
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
        assertEquals("AI 助手暂时不可用，请稍后再试。", result.getReply());
        assertEquals(request.getSessionId(), result.getSessionId());
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
        assertEquals("AI 助手暂时不可用，请稍后再试。", result.getReply());
        assertEquals(request.getSessionId(), result.getSessionId());
    }
}
