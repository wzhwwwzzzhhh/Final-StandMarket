package com.fashion.service.impl;

import com.fashion.config.AgentProperties;
import com.fashion.dto.AgentChatResponse;
import com.fashion.dto.AgentInternalChatRequest;
import com.fashion.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-FSM-Agent-Token";
    private static final String PYTHON_UNAVAILABLE = "PYTHON_AGENT_UNAVAILABLE";
    private static final List<String> REASON_ORDER = Arrays.asList(
            "REDIS_UNAVAILABLE",
            "ELASTICSEARCH_UNAVAILABLE",
            "LLM_UNAVAILABLE",
            "JAVA_TOOL_UNAVAILABLE",
            PYTHON_UNAVAILABLE
    );
    private static final Set<String> ALLOWED_REASONS = new HashSet<>(REASON_ORDER);

    private final RestTemplate restTemplate;
    private final AgentProperties properties;

    public AgentServiceImpl(@Qualifier("agentRestTemplate") RestTemplate restTemplate,
                            AgentProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public AgentChatResponse chat(AgentInternalChatRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(INTERNAL_TOKEN_HEADER, properties.getInternalToken());
            HttpEntity<AgentInternalChatRequest> entity = new HttpEntity<>(request, headers);
            AgentChatResponse response = restTemplate.postForObject(
                    properties.getChatUrl(), entity, AgentChatResponse.class);
            if (!isValidResponse(request, response)) {
                log.warn("Agent dependency contract invalid dependency=python sessionId={}", request.getSessionId());
                return fallbackResponse(request.getSessionId());
            }
            response.setDegradationReasons(normalizeReasons(response.getDegradationReasons()));
            return response;
        } catch (RestClientException e) {
            log.warn("Agent dependency failed dependency=python exceptionType={} sessionId={}",
                    e.getClass().getSimpleName(), request.getSessionId());
            return fallbackResponse(request.getSessionId());
        }
    }

    private boolean isValidResponse(AgentInternalChatRequest request, AgentChatResponse response) {
        if (response == null || !StringUtils.hasText(response.getReply())
                || !request.getSessionId().equals(response.getSessionId())
                || response.getProducts() == null || response.getDegraded() == null
                || response.getDegradationReasons() == null) {
            return false;
        }
        if (!ALLOWED_REASONS.containsAll(response.getDegradationReasons())) {
            return false;
        }
        boolean hasReasons = !response.getDegradationReasons().isEmpty();
        if (response.getDegraded() != hasReasons) {
            return false;
        }
        for (Object productValue : response.getProducts()) {
            if (!(productValue instanceof Map)) {
                return false;
            }
            Map<?, ?> product = (Map<?, ?>) productValue;
            if (product == null || !isPositiveIntegralNumber(product.get("id"))
                    || !StringUtils.hasText(asString(product.get("name")))
                    || !isNonNegativeFiniteNumber(product.get("price"))
                    || !StringUtils.hasText(asString(product.get("image")))) {
                return false;
            }
        }
        return true;
    }

    private String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private boolean isPositiveIntegralNumber(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer
                ? ((Number) value).longValue() > 0
                : value instanceof Long && (Long) value > 0;
    }

    private boolean isNonNegativeFiniteNumber(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number >= 0;
    }

    private List<String> normalizeReasons(List<String> reasons) {
        List<String> normalized = new ArrayList<>();
        for (String reason : REASON_ORDER) {
            if (reasons.contains(reason)) {
                normalized.add(reason);
            }
        }
        return normalized;
    }

    private AgentChatResponse fallbackResponse(String sessionId) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply("AI 助手暂时不可用，请稍后再试。");
        response.setSessionId(sessionId);
        response.setProducts(new ArrayList<>());
        response.setDegraded(true);
        response.setDegradationReasons(new ArrayList<>(Arrays.asList(PYTHON_UNAVAILABLE)));
        return response;
    }
}
