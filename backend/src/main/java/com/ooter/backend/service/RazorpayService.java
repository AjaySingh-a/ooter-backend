package com.ooter.backend.service;

import com.ooter.backend.config.RazorpayConfig;
import com.ooter.backend.exception.PaymentException;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;

    /** Public (publishable) key id for frontend checkout */
    public String getKeyId() {
        return razorpayConfig.getKeyId();
    }

    public String createOrder(int amount, String currency, String receipt, Map<String, String> notes) 
            throws PaymentException {
        try {
            // Amount validation is handled in PaymentController
            // Razorpay minimum is ₹1 (100 paise), but we allow any positive amount for testing
            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }
            
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);
            orderRequest.put("payment_capture", 1);
            
            if (notes != null && !notes.isEmpty()) {
                orderRequest.put("notes", new JSONObject(notes));
            }

            log.debug("Creating Razorpay order with request: {}", orderRequest);
            Order order = razorpayClient.orders.create(orderRequest);
            String orderId = order.get("id");
            log.info("Razorpay order created: {}", orderId);
            
            return orderId;

        } catch (RazorpayException e) {
            String errorMsg = String.format("Razorpay API Error: Message=%s", e.getMessage());
            log.error(errorMsg);
            throw new PaymentException("Payment service unavailable", e);
        } catch (Exception e) {
            log.error("Unexpected error creating order", e);
            throw new PaymentException("Failed to create payment order", e);
        }
    }

    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        try {
            Objects.requireNonNull(orderId, "Order ID cannot be null");
            Objects.requireNonNull(paymentId, "Payment ID cannot be null");
            Objects.requireNonNull(signature, "Signature cannot be null");

            String data = orderId + "|" + paymentId;
            String generatedSignature = generateSignature(data, razorpayConfig.getSecretKey());
            
            // Razorpay sends signatures in hexadecimal format (lowercase)
            // Compare case-insensitively to handle any case variations
            boolean isValid = generatedSignature.equalsIgnoreCase(signature);
            
            log.info("Payment verification for order {}: {}", orderId, isValid ? "VALID" : "INVALID");
            if (!isValid) {
                log.warn("Signature mismatch - Expected: {}, Received: {}", generatedSignature, signature);
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Payment verification failed for order {}", orderId, e);
            return false;
        }
    }

    private String generateSignature(String data, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        sha256Hmac.init(keySpec);
        byte[] hash = sha256Hmac.doFinal(data.getBytes());
        
        // Razorpay uses hexadecimal format (not Base64)
        // Convert byte array to hex string
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }


    private boolean isTestMode() {
        return razorpayConfig.getKeyId().startsWith("rzp_test_");
    }
}