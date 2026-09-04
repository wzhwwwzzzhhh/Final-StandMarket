package com.fashion.agent;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fashion.config.AgentProperties;
import com.fashion.dto.AgentChatResponse;
import com.fashion.dto.AgentInternalChatRequest;
import com.fashion.service.impl.AgentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceImplB9ContractTest {

    private RestTemplate restTemplate;
    private AgentServiceImpl service;
    private AgentProperties properties;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:8000/");
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(10000);
        properties.setInternalToken("0123456789abcdef0123456789abcdef");
        properties.validateAndGetChatUrl(new String[]{"dev"});
        service = new AgentServiceImpl(restTemplate, properties);
    }

    @Test
    void sendsApplicationCredentialAndIndependentInternalDto() {
        AgentInternalChatRequest request = request("abcdefghijklmnopqrstuv");
        AgentChatResponse downstream = healthyResponse(request.getSessionId());
        when(restTemplate.postForObject(eq("http://127.0.0.1:8000/chat"), any(HttpEntity.class),
                eq(AgentChatResponse.class))).thenReturn(downstream);

        AgentChatResponse result = service.chat(request);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("http://127.0.0.1:8000/chat"), entityCaptor.capture(),
                eq(AgentChatResponse.class));
        assertEquals(properties.getInternalToken(),
                entityCaptor.getValue().getHeaders().getFirst("X-FSM-Agent-Token"));
        assertTrue(entityCaptor.getValue().getBody() instanceof AgentInternalChatRequest);
        assertFalse(result.getDegraded());
        assertNotNull(result.getProducts());
        assertNotNull(result.getDegradationReasons());
    }

    @Test
    void timeoutReturnsStableFallbackWithOriginalSession() {
        AgentInternalChatRequest request = request("abcdefghijklmnopqrstuv");
        Logger logger = (Logger) LoggerFactory.getLogger(AgentServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class),
                eq(AgentChatResponse.class))).thenThrow(new ResourceAccessException("secret response body"));

        AgentChatResponse result;
        try {
            result = service.chat(request);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(request.getSessionId(), result.getSessionId());
        assertTrue(result.getDegraded());
        assertEquals(Arrays.asList("PYTHON_AGENT_UNAVAILABLE"), result.getDegradationReasons());
        assertNotNull(result.getProducts());
        assertTrue(result.getProducts().isEmpty());
        String logs = appender.list.toString();
        assertTrue(logs.contains("ResourceAccessException"));
        assertFalse(logs.contains("secret response body"));
        assertFalse(logs.contains(request.getUserAuthorization()));
        assertFalse(logs.contains(request.getMessage()));
    }

    @Test
    void rejectsDifferentButWellFormedDownstreamSession() {
        AgentInternalChatRequest request = request("abcdefghijklmnopqrstuv");
        AgentChatResponse downstream = healthyResponse("zyxwvutsrqponmlkjihgfe");
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class),
                eq(AgentChatResponse.class))).thenReturn(downstream);

        AgentChatResponse result = service.chat(request);

        assertEquals(request.getSessionId(), result.getSessionId());
        assertTrue(result.getDegraded());
        assertEquals(Arrays.asList("PYTHON_AGENT_UNAVAILABLE"), result.getDegradationReasons());
    }

    @Test
    void rejectsNonIntegralIdsAndNegativePricesFromPython() {
        AgentInternalChatRequest request = request("abcdefghijklmnopqrstuv");
        AgentChatResponse downstream = healthyResponse(request.getSessionId());
        Map<String, Object> invalidProduct = new HashMap<>();
        invalidProduct.put("id", 1.5d);
        invalidProduct.put("name", "bad");
        invalidProduct.put("price", -1.0d);
        invalidProduct.put("image", "bad.jpg");
        downstream.getProducts().add(invalidProduct);
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class),
                eq(AgentChatResponse.class))).thenReturn(downstream);

        AgentChatResponse result = service.chat(request);

        assertTrue(result.getDegraded());
        assertEquals(Arrays.asList("PYTHON_AGENT_UNAVAILABLE"), result.getDegradationReasons());
        assertTrue(result.getProducts().isEmpty());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void malformedNonObjectProductReturnsStableFallback() {
        AgentInternalChatRequest request = request("abcdefghijklmnopqrstuv");
        AgentChatResponse downstream = healthyResponse(request.getSessionId());
        downstream.setProducts((java.util.List) Arrays.asList("not-an-object"));
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class),
                eq(AgentChatResponse.class))).thenReturn(downstream);

        AgentChatResponse result = service.chat(request);

        assertTrue(result.getDegraded());
        assertEquals(Arrays.asList("PYTHON_AGENT_UNAVAILABLE"), result.getDegradationReasons());
        assertTrue(result.getProducts().isEmpty());
    }

    private AgentInternalChatRequest request(String sessionId) {
        AgentInternalChatRequest request = new AgentInternalChatRequest();
        request.setUserId((long) Integer.MAX_VALUE + 42L);
        request.setSessionId(sessionId);
        request.setMessage("hello");
        request.setUserAuthorization("Bearer delegated-user-token");
        return request;
    }

    private AgentChatResponse healthyResponse(String sessionId) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply("ok");
        response.setSessionId(sessionId);
        response.setProducts(new ArrayList<>());
        response.setDegraded(false);
        response.setDegradationReasons(new ArrayList<>());
        return response;
    }
}
