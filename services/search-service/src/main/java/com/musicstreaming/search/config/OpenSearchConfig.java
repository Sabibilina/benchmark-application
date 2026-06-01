package com.musicstreaming.search.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.connect-timeout-ms:1000}")
    private int connectTimeoutMs;

    @Value("${opensearch.socket-timeout-ms:5000}")
    private int socketTimeoutMs;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, "http"))
                        .setRequestConfigCallback(config -> config
                                .setConnectTimeout(connectTimeoutMs)
                                .setSocketTimeout(socketTimeoutMs))
        );
    }
}
