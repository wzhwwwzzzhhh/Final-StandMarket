package com.fashion.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Bean
    public RestClient esRestClient() {
        return RestClient.builder(HttpHost.create("http://localhost:9200")).build();
    }
}
