package com.fashion.service.impl;

import com.fashion.entity.Product;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.ProductIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class ProductIndexServiceImpl implements ProductIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexServiceImpl.class);
    private static final String INDEX_NAME = "products";

    @Autowired
    private RestClient restClient;

    @Autowired
    private ProductMapper productMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void rebuildIndex() {
        try {
            deleteIndexIfExists();
            createIndex();
            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("params", new HashMap<>());
            List<Product> products = productMapper.selectByCondition(queryParams);
            bulkIndex(products);
            log.info("ES 全量重建完成，共 {} 条", products.size());
        } catch (Exception e) {
            log.error("ES 全量重建失败", e);
            throw new RuntimeException("ES 全量重建失败: " + e.getMessage());
        }
    }

    @Override
    public void syncProduct(Product product) {
        if (product == null || product.getId() == null) return;
        try {
            Request req = new Request("PUT", "/" + INDEX_NAME + "/_doc/" + product.getId());
            req.setJsonEntity(toDoc(product));
            restClient.performRequest(req);
            log.debug("ES 同步商品 id={}, name={}", product.getId(), product.getName());
        } catch (Exception e) {
            log.error("ES 同步商品失败 id={}", product.getId(), e);
        }
    }

    @Override
    public void deleteProduct(Long productId) {
        if (productId == null) return;
        try {
            Request req = new Request("DELETE", "/" + INDEX_NAME + "/_doc/" + productId);
            restClient.performRequest(req);
            log.debug("ES 删除商品 id={}", productId);
        } catch (IOException e) {
            // 404 表示已不存在，不算错误
            log.debug("ES 删除商品 id={} 忽略", productId);
        }
    }

    @Override
    public Map<String, Object> getIndexStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 文档数
            Request countReq = new Request("GET", "/" + INDEX_NAME + "/_count");
            Response countResp = restClient.performRequest(countReq);
            @SuppressWarnings("unchecked")
            Map<String, Object> countBody = objectMapper.readValue(
                    EntityUtils.toString(countResp.getEntity()), Map.class);
            result.put("docCount", countBody.get("count"));

            // 健康状态
            Request healthReq = new Request("GET", "/_cluster/health");
            Response healthResp = restClient.performRequest(healthReq);
            @SuppressWarnings("unchecked")
            Map<String, Object> healthBody = objectMapper.readValue(
                    EntityUtils.toString(healthResp.getEntity()), Map.class);
            result.put("status", healthBody.get("status"));
            result.put("clusterName", healthBody.get("cluster_name"));
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ====== 私有方法 ======

    private void deleteIndexIfExists() throws IOException {
        Request req = new Request("HEAD", "/" + INDEX_NAME);
        Response resp = restClient.performRequest(req);
        if (resp.getStatusLine().getStatusCode() == 200) {
            restClient.performRequest(new Request("DELETE", "/" + INDEX_NAME));
            log.info("已删除 ES 索引: {}", INDEX_NAME);
        }
    }

    private void createIndex() throws IOException {
        String mapping = "{" +
                "  \"settings\": {" +
                "    \"analysis\": {" +
                "      \"analyzer\": {" +
                "        \"ik_analyzer\": {" +
                "          \"type\": \"custom\"," +
                "          \"tokenizer\": \"ik_max_word\"" +
                "        }," +
                "        \"pinyin_analyzer\": {" +
                "          \"type\": \"custom\"," +
                "          \"tokenizer\": \"my_pinyin\"" +
                "        }" +
                "      }," +
                "      \"tokenizer\": {" +
                "        \"my_pinyin\": {" +
                "          \"type\": \"pinyin\"," +
                "          \"keep_full_pinyin\": true," +
                "          \"keep_joined_full_pinyin\": true," +
                "          \"keep_original\": true," +
                "          \"limit_first_letter_length\": 16," +
                "          \"remove_duplicated_term\": true" +
                "        }" +
                "      }" +
                "    }" +
                "  }," +
                "  \"mappings\": {" +
                "    \"properties\": {" +
                "      \"id\": { \"type\": \"long\" }," +
                "      \"name\": {" +
                "        \"type\": \"text\"," +
                "        \"analyzer\": \"ik_analyzer\"," +
                "        \"fields\": {" +
                "          \"pinyin\": { \"type\": \"text\", \"analyzer\": \"pinyin_analyzer\" }" +
                "        }" +
                "      }," +
                "      \"description\": { \"type\": \"text\", \"analyzer\": \"ik_analyzer\" }," +
                "      \"categoryId\": { \"type\": \"long\" }," +
                "      \"price\": { \"type\": \"double\" }," +
                "      \"image\": { \"type\": \"keyword\", \"index\": false }," +
                "      \"tag\": { \"type\": \"keyword\" }," +
                "      \"status\": { \"type\": \"integer\" }," +
                "      \"stock\": { \"type\": \"integer\" }," +
                "      \"sales\": { \"type\": \"integer\" }" +
                "    }" +
                "  }" +
                "}";

        Request createReq = new Request("PUT", "/" + INDEX_NAME);
        createReq.setJsonEntity(mapping);
        restClient.performRequest(createReq);
        log.info("已创建 ES 索引: {} (IK + Pinyin)", INDEX_NAME);
    }

    private void bulkIndex(List<Product> products) throws IOException {
        int batchSize = 100;
        StringBuilder bulkBody = new StringBuilder();
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            bulkBody.append("{\"index\":{\"_index\":\"")
                    .append(INDEX_NAME).append("\",\"_id\":").append(p.getId()).append("}}\n");
            bulkBody.append(toDoc(p)).append("\n");

            if ((i + 1) % batchSize == 0 || i == products.size() - 1) {
                Request bulkReq = new Request("POST", "/_bulk");
                bulkReq.setJsonEntity(bulkBody.toString());
                restClient.performRequest(bulkReq);
                bulkBody = new StringBuilder();
            }
        }
    }

    private String toDoc(Product p) {
        try {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", p.getId());
            doc.put("name", p.getName());
            doc.put("description", p.getDescription());
            doc.put("categoryId", p.getCategoryId());
            doc.put("price", p.getPrice());
            doc.put("image", p.getImage());
            doc.put("tag", p.getTag());
            doc.put("status", p.getStatus());
            doc.put("stock", p.getStock());
            doc.put("sales", p.getSales() != null ? p.getSales() : 0);
            return objectMapper.writeValueAsString(doc);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }
}
