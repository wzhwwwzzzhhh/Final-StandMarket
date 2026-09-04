package com.fashion.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.product.ProductProjectionProperties;
import com.fashion.service.ProductIndexService;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Read-only ES health surface. All writes go through durable B8 projection tasks. */
@Service
public class ProductIndexServiceImpl implements ProductIndexService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ProductProjectionProperties properties;

    public ProductIndexServiceImpl(RestClient restClient, ObjectMapper objectMapper,
                                   ProductProjectionProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Map<String, Object> getIndexStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            Request countRequest = new Request("GET", "/" + properties.getIndexName() + "/_count");
            Response countResponse = restClient.performRequest(countRequest);
            Map<String, Object> count = objectMapper.readValue(
                    EntityUtils.toString(countResponse.getEntity()), new TypeReference<Map<String, Object>>() { });
            result.put("docCount", count.get("count"));
            Request healthRequest = new Request("GET", "/_cluster/health");
            Response healthResponse = restClient.performRequest(healthRequest);
            Map<String, Object> health = objectMapper.readValue(
                    EntityUtils.toString(healthResponse.getEntity()), new TypeReference<Map<String, Object>>() { });
            result.put("status", health.get("status"));
            result.put("clusterName", health.get("cluster_name"));
        } catch (Exception failure) {
            result.put("status", "error");
            result.put("errorType", failure.getClass().getSimpleName());
        }
        return result;
    }
}
