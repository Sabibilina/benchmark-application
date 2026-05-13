package com.benchmark.catalog;

import com.benchmark.catalog.ingestion.CatalogDatasetProperties;
import com.benchmark.catalog.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({CatalogDatasetProperties.class, JwtProperties.class})
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
