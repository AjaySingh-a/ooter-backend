package com.ooter.backend.config;

import com.razorpay.RazorpayClient;
import com.ooter.backend.exception.PaymentConfigurationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.InitializingBean;

@Slf4j
@Getter
@Configuration
public class RazorpayConfig implements InitializingBean {

    @Value("${razorpay.key_id:}")
    private String keyId;

    @Value("${razorpay.secret_key:}")
    private String secretKey;

    @Value("${razorpay.webhook_secret:}")
    private String webhookSecret;

    @Value("${razorpay.timeout:5000}")
    private int timeout;

    @Override
    public void afterPropertiesSet() {
        if (StringUtils.isBlank(keyId)) {
            throw new PaymentConfigurationException("Razorpay key_id is not configured");
        }
        if (StringUtils.isBlank(secretKey)) {
            throw new PaymentConfigurationException("Razorpay secret_key is not configured");
        }
        log.info("Razorpay configured in {} mode", isTestMode() ? "TEST" : "LIVE");
    }

    @Bean
    public RazorpayClient razorpayClient() {
        try {
            RazorpayClient client = new RazorpayClient(keyId, secretKey);
            log.info("Razorpay client initialized successfully");
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize Razorpay client", e);
            throw new PaymentConfigurationException("Failed to initialize Razorpay client", e);
        }
    }

    public boolean isTestMode() {
        return keyId.startsWith("rzp_test_");
    }

    public boolean isWebhookEnabled() {
        return StringUtils.isNotBlank(webhookSecret);
    }
}