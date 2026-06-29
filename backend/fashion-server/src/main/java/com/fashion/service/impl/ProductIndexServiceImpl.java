package com.fashion.service.impl;

import com.fashion.entity.Product;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.ProductIndexService;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@Service
public class ProductIndexServiceImpl implements ProductIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexServiceImpl.class);
    private static final String INDEX_NAME = "products";

    @Autowired
    private RestClient restClient;

    @Autowired
    private ProductMapper productMapper;

    @Override
    public void rebuildIndex() {
        try {
            createIndexIfNotExists();
            List<Product> products = productMapper.selectByCondition(new HashMap<>());
            int batchSize = 100;
            StringBuilder bulkBody = new StringBuilder();

            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                bulkBody.append(String.format(
                        "{\"index\":{\"_index\":\"%s\",\"_id\":%d}}\n", INDEX_NAME, p.getId()));
                bulkBody.append(String.format(
                        "{\"id\":%d,\"name\":\"%s\",\"description\":\"%s\",\"categoryId\":%d," +
                                "\"price\":%s,\"image\":\"%s\",\"sales\":%d}\n",
                        p.getId(), escapeJson(p.getName()), escapeJson(p.getDescription()),
                        p.getCategoryId(), p.getPrice().toString(),
                        escapeJson(p.getImage()), p.getSales() != null ? p.getSales() : 0));

                if ((i + 1) % batchSize == 0 || i == products.size() - 1) {
                    sendBulkRequest(bulkBody.toString());
                    bulkBody = new StringBuilder();
                }
            }
            log.info("Product index rebuild complete, total: {}", products.size());
        } catch (Exception e) {
            log.error("Failed to rebuild product index", e);
        }
    }

    private void createIndexIfNotExists() throws IOException {
        Request existsReq = new Request("HEAD", "/" + INDEX_NAME);
        Response existsResp = restClient.performRequest(existsReq);
        if (existsResp.getStatusLine().getStatusCode() == 200) {
            return;
        }

        String mapping = "{\n" +
                "  \"settings\": {\n" +
                "    \"analysis\": {\n" +
                "      \"analyzer\": {\n" +
                "        \"ik_smart\": {\n" +
                "          \"type\": \"custom\",\n" +
                "          \"tokenizer\": \"ik_smart\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"mappings\": {\n" +
                "    \"properties\": {\n" +
                "      \"id\": { \"type\": \"long\" },\n" +
                "      \"name\": { \"type\": \"text\", \"analyzer\": \"ik_max_word\" },\n" +
                "      \"description\": { \"type\": \"text\", \"analyzer\": \"ik_max_word\" },\n" +
                "      \"categoryId\": { \"type\": \"long\" },\n" +
                "      \"price\": { \"type\": \"double\" },\n" +
                "      \"image\": { \"type\": \"keyword\" },\n" +
                "      \"sales\": { \"type\": \"integer\" }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        Request createReq = new Request("PUT", "/" + INDEX_NAME);
        createReq.setJsonEntity(mapping);
        restClient.performRequest(createReq);
        log.info("Created ES index: {}", INDEX_NAME);
    }

    private void sendBulkRequest(String body) throws IOException {
        Request bulkReq = new Request("POST", "/_bulk");
        bulkReq.setJsonEntity(body);
        restClient.performRequest(bulkReq);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
