package com.fashion.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.Product;
import com.fashion.entity.ProductProjectionTask;
import com.fashion.product.*;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "b8.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class B8ProductProjectionEsIntegrationTest {
    private RestClient client;
    private ObjectMapper objectMapper;
    private ProductProjectionProperties properties;
    private String index;

    @BeforeAll
    void createIndex() throws Exception {
        String endpoint = System.getProperty("b8.es-url");
        if (endpoint == null) throw new IllegalStateException("b8.es-url is required");
        java.net.URI uri = java.net.URI.create(endpoint);
        B8IntegrationSettings.requireLoopback(uri.getHost(), "Elasticsearch");
        assertEquals("true", System.getProperty("b8.es-exclusive"),
                "B8 ES integration requires explicit exclusive authorization");
        client = RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())).build();
        objectMapper = new ObjectMapper();
        Response root = client.performRequest(new Request("GET", "/"));
        String rootBody = EntityUtils.toString(root.getEntity());
        assertTrue(rootBody.contains("\"number\" : \"8."));
        Response plugins = client.performRequest(new Request("GET", "/_cat/plugins?format=json"));
        String pluginBody = EntityUtils.toString(plugins.getEntity());
        assertTrue(pluginBody.contains("analysis-ik"));
        assertTrue(pluginBody.contains("analysis-pinyin"));
        index = "products_b8_it_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(index.matches("products_b8_it_[0-9a-f]{32}"));
        Request create = new Request("PUT", "/" + index);
        create.setJsonEntity("{\"settings\":{\"index.gc_deletes\":\"5m\"},\"mappings\":{\"properties\":{" +
                "\"id\":{\"type\":\"long\"},\"catalogVersion\":{\"type\":\"long\"}," +
                "\"projectionHash\":{\"type\":\"keyword\"}}}}");
        client.performRequest(create);
        properties = new ProductProjectionProperties();
        properties.setIndexName(index);
    }

    @AfterAll
    void deleteIndex() throws Exception {
        try {
            if (client != null && index != null && index.matches("products_b8_it_[0-9a-f]{32}")) {
                client.performRequest(new Request("DELETE", "/" + index));
                try {
                    client.performRequest(new Request("DELETE", "/" + index + "_missing"));
                } catch (org.elasticsearch.client.ResponseException missing) {
                    if (missing.getResponse().getStatusLine().getStatusCode() != 404) throw missing;
                }
            }
        } finally {
            if (client != null) client.close();
        }
    }

    @Test
    void externalVersioningDuplicateDeleteAndPitScanAreCompatible() throws Exception {
        ElasticsearchProductProjectionDelivery delivery = new ElasticsearchProductProjectionDelivery(
                client, objectMapper, properties);
        ProductProjectionTask first = upsert(7L, 42L, "夹克😀");
        delivery.deliver(first);
        delivery.deliver(first);

        ProductProjectionInventory inventory = new ElasticsearchProductProjectionInventory(
                client, objectMapper, properties);
        IndexedProductProjection indexed = inventory.read(7L);
        assertNotNull(indexed);
        assertEquals(42L, indexed.getCatalogVersion());
        assertEquals(first.getPayloadSha256(), indexed.getProjectionHash());
        client.performRequest(new Request("POST", "/" + index + "/_refresh"));
        ProjectionScanPage page = inventory.scanAfter(null, 10);
        assertEquals(1, page.getItems().size());
        assertNotNull(page.getNextCursor());
        assertTrue(inventory.scanAfter(page.getNextCursor(), 10).getItems().isEmpty());

        ProductProjectionTask delete = tombstone(7L, 43L);
        delivery.deliver(delete);
        delivery.deliver(delete);
        assertNull(inventory.read(7L));

        ProductProjectionTask relisted = upsert(7L, 44L, "重新上架");
        delivery.deliver(relisted);
        assertEquals(44L, inventory.read(7L).getCatalogVersion());

        ProjectionDeliveryException staleDelete = assertThrows(ProjectionDeliveryException.class,
                () -> delivery.deliver(tombstone(7L, 43L)));
        assertFalse(staleDelete.isRetryable());

        ProjectionDeliveryException stale = assertThrows(ProjectionDeliveryException.class,
                () -> delivery.deliver(upsert(7L, 41L, "旧值")));
        assertFalse(stale.isRetryable());
    }

    @Test
    void missingIndexIsNotMistakenForDocumentNotFound() throws Exception {
        ProductProjectionProperties missing = new ProductProjectionProperties();
        missing.setIndexName(index + "_missing");
        ElasticsearchProductProjectionDelivery delivery = new ElasticsearchProductProjectionDelivery(
                client, objectMapper, missing);
        ProjectionDeliveryException failure = assertThrows(ProjectionDeliveryException.class,
                () -> delivery.deliver(tombstone(9L, 50L)));
        assertFalse(failure.isRetryable());
        assertEquals("es_index_probe_http_404", failure.getSafeSummary());
    }

    private ProductProjectionTask upsert(long id, long version, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal("99.00"));
        product.setStatus(1);
        product.setSales(0);
        CanonicalProductProjection projection = new CanonicalProductProjectionCodec().encode(product, version);
        ProductProjectionTask task = base(id, version, "UPSERT");
        task.setPayload(new String(projection.getPayload(), StandardCharsets.UTF_8));
        task.setPayloadSha256(projection.getSha256());
        return task;
    }

    private ProductProjectionTask tombstone(long id, long version) {
        String payload = "{\"productId\":" + id + ",\"catalogVersion\":" + version
                + ",\"itemState\":\"DELETED\"}";
        ProductProjectionTask task = base(id, version, "DELETE");
        task.setPayload(payload);
        task.setPayloadSha256(CanonicalProductProjectionCodec.sha256(
                payload.getBytes(StandardCharsets.UTF_8)));
        return task;
    }

    private ProductProjectionTask base(long id, long version, String operation) {
        ProductProjectionTask task = new ProductProjectionTask();
        task.setTarget("ES");
        task.setProductId(id);
        task.setCatalogVersion(version);
        task.setOperation(operation);
        return task;
    }
}
