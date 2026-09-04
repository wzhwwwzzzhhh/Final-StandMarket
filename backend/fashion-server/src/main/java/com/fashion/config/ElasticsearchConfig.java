package com.fashion.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import com.fashion.product.ProductProjectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    private final String esHost;
    private final ProductProjectionProperties properties;

    public ElasticsearchConfig(
            @org.springframework.beans.factory.annotation.Value(
                    "${fashion.elasticsearch.host:http://localhost:19200}") String esHost,
            ProductProjectionProperties properties) {
        this.esHost = esHost;
        this.properties = properties;
    }

    @Bean
    public RestClient esRestClient() {
        properties.validate();
        return RestClient.builder(HttpHost.create(esHost))
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(toMillis(properties.getConnectTimeout()))
                        .setSocketTimeout(toMillis(properties.getSocketTimeout()))
                        .setConnectionRequestTimeout(toMillis(properties.getConnectionRequestTimeout())))
                .build();
    }

    private int toMillis(java.time.Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Elasticsearch timeout must fit an integer millisecond value");
        }
        return (int) millis;
    }
}
