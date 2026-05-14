package com.benchmark.search.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Bean(destroyMethod = "close")
    RestClient restClient(SearchProperties properties) {
        return RestClient.builder(HttpHost.create(properties.opensearchUrl())).build();
    }
}
