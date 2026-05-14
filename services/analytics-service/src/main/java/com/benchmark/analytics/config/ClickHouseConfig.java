package com.benchmark.analytics.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class ClickHouseConfig {

    @Bean
    DataSource dataSource(AnalyticsProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(properties.clickhouseUrl());
        dataSource.setUsername(properties.clickhouseUser());
        dataSource.setPassword(properties.clickhousePassword());
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        return dataSource;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
