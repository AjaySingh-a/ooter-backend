package com.ooter.backend.config;

import com.ooter.backend.exception.PaymentConfigurationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.InitializingBean;

@Slf4j
@Getter
@Configuration
public class CashfreeConfig implements InitializingBean {

    public static final String API_VERSION = "2023-08-01";

    @Value("${CASHFREE_APP_ID:}")
    private String appId;

    @Value("${CASHFREE_SECRET_KEY:}")
    private String secretKey;

    private final String baseUrl = "https://api.cashfree.com/pg";

    @Override
    public void afterPropertiesSet() {
        if (StringUtils.isBlank(appId)) {
            throw new PaymentConfigurationException("CASHFREE_APP_ID is not configured");
        }
        if (StringUtils.isBlank(secretKey)) {
            throw new PaymentConfigurationException("CASHFREE_SECRET_KEY is not configured");
        }
        log.info("Cashfree configured (production)");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
