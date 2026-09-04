package com.fashion.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.ProductProjectionTask;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ElasticsearchProductProjectionDelivery implements ProductProjectionDelivery {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ProductProjectionProperties properties;

    public ElasticsearchProductProjectionDelivery(RestClient restClient, ObjectMapper objectMapper,
                                                   ProductProjectionProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String target() {
        return "ES";
    }

    @Override
    public void deliver(ProductProjectionTask task) {
        validate(task);
        if ("DELETE".equals(task.getOperation())) {
            requireExistingIndex();
        }
        Request request = new Request("UPSERT".equals(task.getOperation()) ? "PUT" : "DELETE",
                "/" + properties.getIndexName() + "/_doc/" + task.getProductId());
        request.addParameter("version", Long.toString(task.getCatalogVersion()));
        request.addParameter("version_type", "external_gte");
        if ("UPSERT".equals(task.getOperation())) {
            request.setJsonEntity(upsertBody(task));
        }
        try {
            restClient.performRequest(request);
        } catch (ResponseException responseFailure) {
            handleResponseFailure(responseFailure, task.getOperation());
        } catch (IOException transportFailure) {
            throw ProjectionDeliveryException.retryable(
                    "es_transport_" + transportFailure.getClass().getSimpleName(), transportFailure);
        }
    }

    private void requireExistingIndex() {
        try {
            Response response = restClient.performRequest(
                    new Request("HEAD", "/" + properties.getIndexName()));
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status >= 300) {
                if (status == 408 || status == 429 || status >= 500) {
                    throw ProjectionDeliveryException.retryable("es_index_probe_http_" + status);
                }
                throw ProjectionDeliveryException.permanent("es_index_probe_http_" + status);
            }
        } catch (ResponseException failure) {
            int status = failure.getResponse().getStatusLine().getStatusCode();
            if (status == 408 || status == 429 || status >= 500) {
                throw ProjectionDeliveryException.retryable("es_index_probe_http_" + status, failure);
            }
            throw ProjectionDeliveryException.permanent("es_index_probe_http_" + status, failure);
        } catch (IOException failure) {
            throw ProjectionDeliveryException.retryable(
                    "es_index_probe_" + failure.getClass().getSimpleName(), failure);
        }
    }

    private void validate(ProductProjectionTask task) {
        if (task == null || task.getProductId() == null || task.getProductId() <= 0
                || task.getCatalogVersion() == null || task.getCatalogVersion() <= 0
                || !("UPSERT".equals(task.getOperation()) || "DELETE".equals(task.getOperation()))
                || task.getPayload() == null || task.getPayloadSha256() == null) {
            throw ProjectionDeliveryException.permanent("invalid_es_projection_task");
        }
        String actual = CanonicalProductProjectionCodec.sha256(
                task.getPayload().getBytes(StandardCharsets.UTF_8));
        if (!actual.equals(task.getPayloadSha256())) {
            throw ProjectionDeliveryException.permanent("es_payload_hash_mismatch");
        }
    }

    private String upsertBody(ProductProjectionTask task) {
        try {
            LinkedHashMap<String, Object> document = objectMapper.readValue(
                    task.getPayload(), new TypeReference<LinkedHashMap<String, Object>>() { });
            Object version = document.get("catalogVersion");
            if (!(version instanceof Number)
                    || ((Number) version).longValue() != task.getCatalogVersion()) {
                throw ProjectionDeliveryException.permanent("es_payload_version_mismatch");
            }
            document.put("projectionHash", task.getPayloadSha256());
            document.remove("stock");
            return objectMapper.writeValueAsString(document);
        } catch (ProjectionDeliveryException failure) {
            throw failure;
        } catch (IOException failure) {
            throw ProjectionDeliveryException.permanent("es_payload_invalid_json", failure);
        }
    }

    private void handleResponseFailure(ResponseException failure, String operation) {
        Response response = failure.getResponse();
        int status = response.getStatusLine().getStatusCode();
        String errorType = null;
        String result = null;
        try {
            if (response.getEntity() != null) {
                String body = EntityUtils.toString(response.getEntity());
                Map<String, Object> parsed = objectMapper.readValue(
                        body, new TypeReference<Map<String, Object>>() { });
                result = parsed.get("result") == null ? null : parsed.get("result").toString();
                Object error = parsed.get("error");
                if (error instanceof Map) {
                    Object type = ((Map<?, ?>) error).get("type");
                    errorType = type == null ? null : type.toString();
                }
            }
        } catch (Exception ignored) {
            errorType = null;
            result = null;
        }
        EsDeliveryDisposition disposition = classifyStatus(status, errorType, result, operation);
        if (disposition == EsDeliveryDisposition.SUCCESS) {
            return;
        }
        String summary = "es_http_" + status + (errorType == null ? "" : "_" + errorType);
        if (disposition == EsDeliveryDisposition.RETRYABLE) {
            throw ProjectionDeliveryException.retryable(summary, failure);
        }
        throw ProjectionDeliveryException.permanent(summary, failure);
    }

    static EsDeliveryDisposition classifyStatus(int status, String errorType,
                                                 String result, String operation) {
        if (status >= 200 && status < 300) {
            return EsDeliveryDisposition.SUCCESS;
        }
        if (status == 404 && "DELETE".equals(operation)
                && errorType == null && "not_found".equals(result)) {
            return EsDeliveryDisposition.SUCCESS;
        }
        if (status == 408 || status == 429 || status >= 500) {
            return EsDeliveryDisposition.RETRYABLE;
        }
        return EsDeliveryDisposition.PERMANENT;
    }
}
