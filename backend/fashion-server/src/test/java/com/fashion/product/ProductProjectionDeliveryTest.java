package com.fashion.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.ProductProjectionTask;
import com.fashion.utils.CacheClient;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductProjectionDeliveryTest {

    @Test
    void redisDeliveryPublishesListAndDetailMonotonically() {
        CacheClient cache = mock(CacheClient.class);
        when(cache.publishMaxVersion(anyString(), eq(42L))).thenReturn(1L);
        RedisProductProjectionDelivery delivery = new RedisProductProjectionDelivery(cache);
        ProductProjectionTask task = task("REDIS", "PUBLISH");

        delivery.deliver(task);

        verify(cache).publishMaxVersion(ProductCacheKeys.LIST_PUBLISHED_VERSION, 42L);
        verify(cache).publishMaxVersion(ProductCacheKeys.detailPublishedVersion(7L), 42L);
    }

    @Test
    void redisFailedPublishRemainsRetryable() {
        CacheClient cache = mock(CacheClient.class);
        when(cache.publishMaxVersion(ProductCacheKeys.LIST_PUBLISHED_VERSION, 42L)).thenReturn(0L);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new RedisProductProjectionDelivery(cache).deliver(task("REDIS", "PUBLISH")))
                .isInstanceOf(ProjectionDeliveryException.class)
                .extracting(error -> ((ProjectionDeliveryException) error).isRetryable())
                .isEqualTo(true);
    }

    @Test
    void esUpsertUsesExternalGteAndAddsHashWithoutStock() throws Exception {
        RestClient client = mock(RestClient.class);
        ProductProjectionProperties properties = new ProductProjectionProperties();
        properties.setIndexName("products_b8_it_deadbeef");
        ElasticsearchProductProjectionDelivery delivery = new ElasticsearchProductProjectionDelivery(
                client, new ObjectMapper(), properties);
        ProductProjectionTask task = task("ES", "UPSERT");
        task.setPayload("{\"id\":7,\"catalogVersion\":42,\"sales\":0}");
        task.setPayloadSha256(CanonicalProductProjectionCodec.sha256(
                task.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        delivery.deliver(task);

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(client).performRequest(request.capture());
        assertThat(request.getValue().getMethod()).isEqualTo("PUT");
        assertThat(request.getValue().getEndpoint()).isEqualTo("/products_b8_it_deadbeef/_doc/7");
        assertThat(request.getValue().getParameters()).containsEntry("version", "42")
                .containsEntry("version_type", "external_gte");
        String body = org.apache.http.util.EntityUtils.toString(request.getValue().getEntity());
        assertThat(body).contains("\"projectionHash\":\"" + task.getPayloadSha256() + "\"")
                .doesNotContain("stock");
    }

    @Test
    void statusClassificationOnlyTreatsDocumentNotFoundDeleteAsIdempotent() {
        assertThat(ElasticsearchProductProjectionDelivery.classifyStatus(404, null, "not_found", "DELETE"))
                .isEqualTo(EsDeliveryDisposition.SUCCESS);
        assertThat(ElasticsearchProductProjectionDelivery.classifyStatus(
                404, "index_not_found_exception", null, "DELETE"))
                .isEqualTo(EsDeliveryDisposition.PERMANENT);
        assertThat(ElasticsearchProductProjectionDelivery.classifyStatus(503, null, null, "UPSERT"))
                .isEqualTo(EsDeliveryDisposition.RETRYABLE);
        assertThat(ElasticsearchProductProjectionDelivery.classifyStatus(400, "mapper_parsing_exception", null, "UPSERT"))
                .isEqualTo(EsDeliveryDisposition.PERMANENT);
    }

    private ProductProjectionTask task(String target, String operation) {
        ProductProjectionTask task = new ProductProjectionTask();
        task.setId(1L);
        task.setTarget(target);
        task.setProductId(7L);
        task.setCatalogVersion(42L);
        task.setOperation(operation);
        task.setPayload("{\"productId\":7,\"catalogVersion\":42}");
        task.setPayloadSha256("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        return task;
    }
}
