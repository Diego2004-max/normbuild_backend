package com.normbuild.config;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfiguration {

    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String configuredUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return DataSourceBuilder.create()
                .url(normalizeJdbcUrl(configuredUrl))
                .username(username)
                .password(password)
                .build();
    }

    private String normalizeJdbcUrl(String url) {
        if (url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("postgres://")) {
            return normalizeRenderPostgresUrl(url);
        }
        if (url.startsWith("postgresql://")) {
            return normalizeRenderPostgresUrl(url);
        }
        throw new IllegalArgumentException("DATABASE_URL must use jdbc:postgresql:// or postgresql://");
    }

    private String normalizeRenderPostgresUrl(String url) {
        try {
            URI parsed = new URI(url);
            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(parsed.getHost());
            if (parsed.getPort() >= 0) {
                jdbcUrl.append(':').append(parsed.getPort());
            }
            if (parsed.getRawPath() != null) {
                jdbcUrl.append(parsed.getRawPath());
            }
            if (parsed.getRawQuery() != null) {
                jdbcUrl.append('?').append(parsed.getRawQuery());
            }
            return jdbcUrl.toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("DATABASE_URL is not a valid PostgreSQL URL", exception);
        }
    }
}
