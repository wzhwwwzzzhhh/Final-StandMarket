package com.fashion.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ElasticsearchProductProjectionInventory implements ProductProjectionInventory {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final ProductProjectionProperties properties;

    public ElasticsearchProductProjectionInventory(RestClient client, ObjectMapper objectMapper,
                                                   ProductProjectionProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public IndexedProductProjection read(long productId) {
        Request request = new Request("GET", "/" + properties.getIndexName() + "/_doc/" + productId);
        request.addParameter("_source_includes", "catalogVersion,projectionHash");
        try {
            Response response = client.performRequest(request);
            return parseDocument(objectMapper, EntityUtils.toString(response.getEntity()));
        } catch (ResponseException failure) {
            if (failure.getResponse().getStatusLine().getStatusCode() == 404
                    && !hasErrorType(failure.getResponse(), "index_not_found_exception")) {
                return null;
            }
            throw new IllegalStateException("product projection inventory read failed", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("product projection inventory read failed", failure);
        }
    }

    @Override
    public ProjectionScanPage scanAfter(String encodedCursor, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("invalid scan limit");
        ScanCursor cursor = encodedCursor == null ? new ScanCursor(openPit(), null)
                : decodeCursor(objectMapper, encodedCursor);
        Request request = new Request("POST", "/_search");
        request.setJsonEntity(searchBody(cursor, limit));
        try {
            Response response = client.performRequest(request);
            String body = EntityUtils.toString(response.getEntity());
            ProjectionScanPage page = parseSearch(objectMapper, body);
            if (page.getItems().isEmpty()) {
                closePit(cursor.pitId);
                return ProjectionScanPage.end();
            }
            return page;
        } catch (ResponseException failure) {
            if (failure.getResponse().getStatusLine().getStatusCode() == 404
                    && hasErrorType(failure.getResponse(), "search_context_missing_exception")) {
                throw new ProductProjectionScanResetException("product projection PIT expired", failure);
            }
            throw new IllegalStateException("product projection inventory scan failed", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("product projection inventory scan failed", failure);
        }
    }

    private String openPit() {
        Request request = new Request("POST", "/" + properties.getIndexName() + "/_pit");
        request.addParameter("keep_alive", "1m");
        try {
            Response response = client.performRequest(request);
            Map<String, Object> body = objectMapper.readValue(EntityUtils.toString(response.getEntity()),
                    new TypeReference<Map<String, Object>>() { });
            Object id = body.get("id");
            if (id == null || id.toString().isEmpty()) throw new IOException("PIT id missing");
            return id.toString();
        } catch (IOException failure) {
            throw new IllegalStateException("product projection PIT open failed", failure);
        }
    }

    private void closePit(String pitId) {
        Request request = new Request("DELETE", "/_pit");
        request.setJsonEntity("{\"id\":" + quote(pitId) + "}");
        try {
            client.performRequest(request);
        } catch (IOException ignored) {
            // PIT expiry is bounded by keep_alive; closing failure must not invalidate a completed page.
        }
    }

    private String searchBody(ScanCursor cursor, int limit) {
        StringBuilder body = new StringBuilder();
        body.append("{\"size\":").append(limit)
                .append(",\"_source\":[\"catalogVersion\",\"projectionHash\"]")
                .append(",\"pit\":{\"id\":").append(quote(cursor.pitId))
                .append(",\"keep_alive\":\"1m\"},\"sort\":[{\"id\":\"asc\"}]");
        if (cursor.lastSort != null) {
            body.append(",\"search_after\": [").append(cursor.lastSort).append(']');
        }
        return body.append('}').toString();
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException impossible) {
            throw new IllegalStateException("cursor encoding failed", impossible);
        }
    }

    static IndexedProductProjection parseDocument(ObjectMapper objectMapper, String body) throws IOException {
        Map<String, Object> root = objectMapper.readValue(body,
                new TypeReference<Map<String, Object>>() { });
        Object id = root.get("_id");
        Object sourceValue = root.get("_source");
        if (id == null || !(sourceValue instanceof Map)) throw new IOException("invalid ES document metadata");
        Map<?, ?> source = (Map<?, ?>) sourceValue;
        Object version = source.get("catalogVersion");
        Object hash = source.get("projectionHash");
        if (!(version instanceof Number) || hash == null) throw new IOException("projection metadata missing");
        return new IndexedProductProjection(Long.parseLong(id.toString()),
                ((Number) version).longValue(), hash.toString());
    }

    static ProjectionScanPage parseSearch(ObjectMapper objectMapper, String body) throws IOException {
        Map<String, Object> root = objectMapper.readValue(body,
                new TypeReference<Map<String, Object>>() { });
        Object pitValue = root.get("pit_id");
        if (pitValue == null) throw new IOException("PIT id missing from search response");
        Object hitsValue = root.get("hits");
        if (!(hitsValue instanceof Map)) throw new IOException("search hits missing");
        Object itemsValue = ((Map<?, ?>) hitsValue).get("hits");
        if (!(itemsValue instanceof List)) throw new IOException("search hit list missing");
        List<IndexedProductProjection> items = new ArrayList<>();
        Long lastSort = null;
        for (Object value : (List<?>) itemsValue) {
            if (!(value instanceof Map)) throw new IOException("invalid search hit");
            Map<?, ?> hit = (Map<?, ?>) value;
            items.add(parseDocument(objectMapper, objectMapper.writeValueAsString(hit)));
            Object sortValue = hit.get("sort");
            if (!(sortValue instanceof List) || ((List<?>) sortValue).isEmpty()
                    || !(((List<?>) sortValue).get(0) instanceof Number)) {
                throw new IOException("stable numeric sort missing");
            }
            lastSort = ((Number) ((List<?>) sortValue).get(0)).longValue();
        }
        String cursor = lastSort == null ? null
                : encodeCursor(objectMapper, new ScanCursor(pitValue.toString(), lastSort));
        return ProjectionScanPage.of(items, cursor);
    }

    private static boolean hasErrorType(Response response, String type) {
        try {
            if (response.getEntity() == null) return false;
            String body = EntityUtils.toString(response.getEntity());
            return body.contains("\"type\":\"" + type + "\"");
        } catch (IOException ignored) {
            return false;
        }
    }

    static String encodeCursor(ObjectMapper objectMapper, ScanCursor cursor) throws IOException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("pit", cursor.pitId);
        value.put("sort", cursor.lastSort);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                objectMapper.writeValueAsBytes(value));
    }

    static ScanCursor decodeCursor(ObjectMapper objectMapper, String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
            Map<String, Object> value = objectMapper.readValue(decoded,
                    new TypeReference<Map<String, Object>>() { });
            Object pit = value.get("pit");
            Object sort = value.get("sort");
            if (pit == null || !(sort instanceof Number)) throw new IOException("invalid cursor fields");
            return new ScanCursor(pit.toString(), ((Number) sort).longValue());
        } catch (Exception failure) {
            throw new IllegalArgumentException("invalid product projection cursor", failure);
        }
    }

    static final class ScanCursor {
        private final String pitId;
        private final Long lastSort;
        ScanCursor(String pitId, Long lastSort) { this.pitId = pitId; this.lastSort = lastSort; }
        String getPitId() { return pitId; }
        Long getLastSort() { return lastSort; }
    }
}
