package com.ooter.backend.service;

import com.ooter.backend.config.CashfreeConfig;
import com.ooter.backend.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashfreeService {

    private final CashfreeConfig cashfreeConfig;
    private final RestTemplate restTemplate;

    /**
     * Create order at Cashfree. Returns order_id and payment_session_id for frontend checkout.
     */
    public CreateOrderResult createOrder(double orderAmountRupees, String currency, String customerId,
                                        String customerPhone, String customerEmail, String returnUrl,
                                        Map<String, String> orderTags) {
        if (orderAmountRupees < 1.0) {
            throw new IllegalArgumentException("Cashfree minimum order amount is ₹1");
        }
        String orderId = "ooter_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        Map<String, Object> body = new HashMap<>();
        body.put("order_id", orderId);
        body.put("order_amount", orderAmountRupees);
        body.put("order_currency", currency != null ? currency : "INR");

        Map<String, Object> customerDetails = new HashMap<>();
        customerDetails.put("customer_id", customerId != null ? customerId : "user_" + System.currentTimeMillis());
        if (customerPhone != null && !customerPhone.isEmpty()) {
            customerDetails.put("customer_phone", customerPhone.length() >= 10 ? customerPhone.substring(customerPhone.length() - 10) : customerPhone);
        } else {
            customerDetails.put("customer_phone", "9999999999");
        }
        if (customerEmail != null && !customerEmail.isEmpty()) {
            customerDetails.put("customer_email", customerEmail);
        }
        body.put("customer_details", customerDetails);

        if (returnUrl != null && !returnUrl.isEmpty()) {
            Map<String, Object> orderMeta = new HashMap<>();
            orderMeta.put("return_url", returnUrl);
            body.put("order_meta", orderMeta);
        }
        if (orderTags != null && !orderTags.isEmpty()) {
            body.put("order_tags", orderTags);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-version", CashfreeConfig.API_VERSION);
        headers.set("x-client-id", cashfreeConfig.getAppId());
        headers.set("x-client-secret", cashfreeConfig.getSecretKey());
        headers.set("x-request-id", UUID.randomUUID().toString());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = cashfreeConfig.getBaseUrl() + "/orders";

        try {
            logCurrentEgressIp();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> res = response.getBody();
                String cfOrderId = (String) res.get("cf_order_id");
                String paymentSessionId = (String) res.get("payment_session_id");
                Object orderAmount = res.get("order_amount");
                String orderCurrency = (String) res.get("order_currency");
                log.info("Cashfree order created: order_id={}, cf_order_id={}", orderId, cfOrderId);
                return new CreateOrderResult(
                        (String) res.get("order_id"),
                        paymentSessionId != null ? paymentSessionId : "",
                        orderAmount != null ? ((Number) orderAmount).doubleValue() : orderAmountRupees,
                        orderCurrency != null ? orderCurrency : "INR"
                );
            }
            throw new PaymentException("Cashfree create order returned unexpected response");
        } catch (Exception e) {
            log.error("Cashfree create order failed", e);
            if (e instanceof PaymentException) throw (PaymentException) e;
            throw new PaymentException("Failed to create payment order: " + e.getMessage(), e);
        }
    }

    /**
     * Verify payment by fetching order status from Cashfree. Returns true if order_status is PAID.
     */
    public boolean verifyPayment(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return false;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-version", CashfreeConfig.API_VERSION);
        headers.set("x-client-id", cashfreeConfig.getAppId());
        headers.set("x-client-secret", cashfreeConfig.getSecretKey());
        headers.set("x-request-id", UUID.randomUUID().toString());

        HttpEntity<Void> request = new HttpEntity<>(headers);
        String url = cashfreeConfig.getBaseUrl() + "/orders/" + orderId;

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String orderStatus = (String) response.getBody().get("order_status");
                boolean paid = "PAID".equalsIgnoreCase(orderStatus);
                log.info("Cashfree order {} verification: order_status={}, valid={}", orderId, orderStatus, paid);
                return paid;
            }
            return false;
        } catch (Exception e) {
            log.error("Cashfree verify payment failed for order {}", orderId, e);
            return false;
        }
    }

    /**
     * Get payment id for order (e.g. cf_payment_id) from Cashfree if needed for booking transaction_id.
     * Optional: GET /orders/{order_id}/payments or use order_id as transaction reference.
     */
    public String getPaymentIdForOrder(String orderId) {
        // Cashfree Get Payments for Order - we can use order_id as transaction ref for simplicity
        return orderId;
    }

    /** Log current outbound IP (for Cashfree PG IP whitelist debugging). */
    private void logCurrentEgressIp() {
        try {
            String ip = restTemplate.getForObject("https://api.ipify.org", String.class);
            log.info("CASHFREE_EGRESS_IP: {} (add this in Payment Gateway dashboard if 403 IP not allowed)", ip != null ? ip.trim() : "unknown");
        } catch (Exception e) {
            log.warn("Could not fetch egress IP for logging: {}", e.getMessage());
        }
    }

    @lombok.Value
    public static class CreateOrderResult {
        String orderId;
        String paymentSessionId;
        double orderAmount;
        String orderCurrency;
    }
}
