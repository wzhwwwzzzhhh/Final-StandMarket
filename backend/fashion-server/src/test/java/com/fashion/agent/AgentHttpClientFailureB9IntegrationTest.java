package com.fashion.agent;

import com.fashion.config.AgentHttpClientConfig;
import com.fashion.config.AgentProperties;
import com.fashion.dto.AgentChatResponse;
import com.fashion.dto.AgentInternalChatRequest;
import com.fashion.service.impl.AgentServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentHttpClientFailureB9IntegrationTest {

    @Test
    void connectionRefusalReturnsFallbackWithinTheConfiguredBudget() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        AgentServiceImpl service = serviceFor("http://127.0.0.1:" + unusedPort, 200, 200);

        AgentChatResponse response = assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> service.chat(request()));

        assertFallback(response);
    }

    @Test
    void readTimeoutReturnsFallbackWithoutWaitingForTheSlowBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            try {
                Thread.sleep(600);
                byte[] body = "{}".getBytes("UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (Exception ignored) {
                // The client is expected to close the timed-out exchange.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            AgentServiceImpl service = serviceFor(
                    "http://127.0.0.1:" + server.getAddress().getPort(), 200, 100);

            AgentChatResponse response = assertTimeoutPreemptively(
                    Duration.ofSeconds(2), () -> service.chat(request()));

            assertFallback(response);
        } finally {
            server.stop(0);
        }
    }

    private AgentServiceImpl serviceFor(String baseUrl, int connectTimeout, int readTimeout) {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeoutMs(connectTimeout);
        properties.setReadTimeoutMs(readTimeout);
        properties.setInternalToken("0123456789abcdef0123456789abcdef");
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        RestTemplate template = new AgentHttpClientConfig().agentRestTemplate(properties, environment);
        return new AgentServiceImpl(template, properties);
    }

    private AgentInternalChatRequest request() {
        AgentInternalChatRequest request = new AgentInternalChatRequest();
        request.setUserId(1L);
        request.setSessionId("abcdefghijklmnopqrstuv");
        request.setMessage("hello");
        request.setUserAuthorization("Bearer delegated-user-token");
        return request;
    }

    private void assertFallback(AgentChatResponse response) {
        assertTrue(response.getDegraded());
        assertEquals("abcdefghijklmnopqrstuv", response.getSessionId());
        assertEquals("PYTHON_AGENT_UNAVAILABLE", response.getDegradationReasons().get(0));
        assertTrue(response.getProducts().isEmpty());
    }
}
