package com.ooter.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Fix URL to ensure SSL parameters are included for Supabase
        String fixedUrl = fixDatabaseUrl(dbUrl);
        config.setJdbcUrl(fixedUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName("org.postgresql.Driver");
        
        // SSL Configuration for Supabase
        Properties dataSourceProperties = new Properties();
        dataSourceProperties.setProperty("sslmode", "require");
        dataSourceProperties.setProperty("ssl", "true");
        // Use NonValidatingFactory to avoid certificate validation issues
        dataSourceProperties.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory");
        config.setDataSourceProperties(dataSourceProperties);
        
        // Connection Pool Settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000); // 30 seconds
        config.setMaxLifetime(300000); // 5 minutes
        config.setIdleTimeout(30000); // 30 seconds
        config.setLeakDetectionThreshold(60000); // 1 minute
        config.setConnectionTestQuery("SELECT 1");
        
        // Additional settings for better connection handling
        config.setInitializationFailTimeout(-1); // Don't fail fast, keep retrying
        config.setValidationTimeout(5000); // 5 seconds for validation
        
        logger.info("Configuring DataSource with URL: {}", maskUrl(fixedUrl));
        
        return new HikariDataSource(config);
    }
    
    /**
     * Fixes the database URL to ensure SSL parameters are included
     */
    private String fixDatabaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Database URL cannot be null or empty. Please set SUPABASE_DB_URL environment variable.");
        }
        
        // If URL already has query parameters, append SSL params
        if (url.contains("?")) {
            if (!url.contains("sslmode=")) {
                url += "&sslmode=require";
            }
            if (!url.contains("ssl=true")) {
                url += "&ssl=true";
            }
        } else {
            // No query parameters, add them
            url += "?sslmode=require&ssl=true";
        }
        
        return url;
    }
    
    /**
     * Masks sensitive information in URL for logging
     */
    private String maskUrl(String url) {
        if (url == null) return "null";
        // Mask password if present in URL
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:***@");
    }
}

