package com.fashion.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.ProductProjectionTask;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchProductProjectionDeliveryHttpTest {
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer server;
    private RestClient client;
    private ElasticsearchProductProjectionDelivery delivery;

    @BeforeEach
    void startLoopbackFaultServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = RestClient.builder(new HttpHost("127.0.0.1", server.getAddress().getPort(), "http"))
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(100).setConnectionRequestTimeout(100).setSocketTimeout(100))
                .build();
        ProductProjectionProperties properties = new ProductProjectionProperties();
        properties.setIndexName("products_fault_test");
        delivery = new ElasticsearchProductProjectionDelivery(client, new ObjectMapper(), properties);
    }

    @AfterEach
    void stopLoopbackFaultServer() throws Exception {
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    @Test
    void timeout429And503AreRetryableWhileMappingAndAuth4xxAreTerminal() {
        status.set(0);
        assertRetryable("es_transport_");
        status.set(429);
        assertRetryable("es_http_429");
        status.set(503);
        assertRetryable("es_http_503");

        status.set(400);
        assertTerminal("es_http_400");
        status.set(401);
        assertTerminal("es_http_401");
        status.set(403);
        assertTerminal("es_http_403");
    }

    private void assertRetryable(String summaryPrefix) {
        assertThatThrownBy(() -> delivery.deliver(upsert()))
                .isInstanceOfSatisfying(ProjectionDeliveryException.class, failure -> {
                    assertThat(failure.isRetryable()).isTrue();
                    assertThat(failure.getSafeSummary()).startsWith(summaryPrefix);
                });
    }

    private void assertTerminal(String summaryPrefix) {
        assertThatThrownBy(() -> delivery.deliver(upsert()))
                .isInstanceOfSatisfying(ProjectionDeliveryException.class, failure -> {
                    assertThat(failure.isRetryable()).isFalse();
                    assertThat(failure.getSafeSummary()).startsWith(summaryPrefix);
                });
    }

    private ProductProjectionTask upsert() {
        String payload = "{\"id\":7,\"catalogVersion\":42,\"sales\":0}";
        ProductProjectionTask task = new ProductProjectionTask();
        task.setTarget("ES");
        task.setProductId(7L);
        task.setCatalogVersion(42L);
        task.setOperation("UPSERT");
        task.setPayload(payload);
        task.setPayloadSha256(CanonicalProductProjectionCodec.sha256(
                payload.getBytes(StandardCharsets.UTF_8)));
        return task;
    }

    private void respond(HttpExchange exchange) throws IOException {
        int current = status.get();
        if (current == 0) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            current = 200;
        }
        String type = current == 400 ? "mapper_parsing_exception"
                : current == 429 ? "es_rejected_execution_exception"
                : current >= 500 ? "unavailable_shards_exception"
                : current == 401 ? "security_exception" : "authorization_exception";
        byte[] body = (current >= 200 && current < 300 ? "{\"result\":\"updated\"}"
                : "{\"error\":{\"type\":\"" + type + "\"}}").getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(current, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
