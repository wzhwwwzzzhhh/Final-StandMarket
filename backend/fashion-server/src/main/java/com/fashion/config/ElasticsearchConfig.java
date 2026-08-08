package com.fashion.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${fashion.elasticsearch.host:http://localhost:19200}")
    private String esHost;

    @Bean
    public RestClient esRestClient() {
        return RestClient.builder(HttpHost.create(esHost)).build();
    }
}
