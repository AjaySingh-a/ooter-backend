package com.ooter.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import org.json.JSONObject;

@Slf4j
@Component
public class Fast2SMS {
    
    @Value("${fast2sms.api.key}")
    private String apiKey;
    
    @Value("${dlt.template.id}")
    private String dltTemplateId;
    
    @Value("${dlt.header.name}")
    private String dltHeaderName;
    
    @Value("${dlt.entity.id}")
    private String dltEntityId;
    
    // ✅ Method for sending OTP SMS with DLT template
    public void sendOtpSms(String phone, String otp) throws Exception {
        // Format phone number (remove +91 if present)
        String formattedPhone = phone.replace("+91", "").replaceAll("\\s", "");
        
        // Validate phone number
        if (formattedPhone.length() != 10) {
            throw new RuntimeException("Invalid phone number format");
        }
        
        // ✅ Fast2SMS DLT Manual API (as per docs):
        // - route MUST be "dlt_manual"
        // - message MUST be the FULL DLT-approved message with {#VAR#} replaced by the real OTP value
        // - If template is added in DLT Manager, entity_id & template_id can be skipped (Fast2SMS auto-matches by message text)
        final String approvedTemplate =
                "ADBOOK COMMUNICATION PRIVATE LIMITED: Your OOTER verification code is {#VAR#}. This code is valid for 5 minutes. Do not share it with anyone.";
        final String messageToSend = approvedTemplate
                .replace("{#VAR#}", otp)
                .replace("{#var#}", otp); // extra safety if template variable case differs
        
        String url = "https://www.fast2sms.com/dev/bulkV2";
        
        // ✅ Fast2SMS DLT Manual (Single) POST payload
        JSONObject jsonPayload = new JSONObject();
        jsonPayload.put("route", "dlt_manual"); // ✅ CRITICAL: DLT Manual route
        jsonPayload.put("sender_id", dltHeaderName); // ✅ DLT Approved Sender ID (Header)
        jsonPayload.put("message", messageToSend); // ✅ FULL approved message with OTP substituted
        jsonPayload.put("numbers", formattedPhone);
        
        log.info("=== Fast2SMS DLT MANUAL SMS Request ===");
        log.info("Phone: {}", formattedPhone);
        log.info("Sender ID (Header Name): {}", dltHeaderName);
        log.info("OTP: {}", otp);
        log.info("Route: dlt_manual");
        log.info("Note: Message field contains FULL approved message (DLT Manual). template_id/entity_id omitted (DLT Manager auto-match).");
        log.info("API Key: {}", apiKey != null ? apiKey.substring(0, 10) + "..." : "NULL");
        log.info("JSON Payload: {}", jsonPayload.toString());
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("authorization", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload.toString()))
                .build();
        
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        log.info("=== Fast2SMS Response ===");
        log.info("Status Code: {}", response.statusCode());
        log.info("Response Body: {}", response.body());
        
        // Check if response indicates success
        if (response.statusCode() != 200) {
            String errorMsg = "SMS sending failed with HTTP status " + response.statusCode() + ": " + response.body();
            log.error("ERROR: {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        // Parse JSON response to check if SMS was actually sent
        try {
            JSONObject jsonResponse = new JSONObject(response.body());
            boolean success = jsonResponse.optBoolean("return", false);
            log.info("SMS API Return Status: {}", success);
            
            if (!success) {
                String errorMsg = jsonResponse.optString("message", "Unknown error");
                String requestId = jsonResponse.optString("request_id", "");
                log.error("ERROR: SMS API returned false");
                log.error("Error Message: {}", errorMsg);
                log.error("Request ID: {}", requestId);
                
                // Check for specific error codes
                if (errorMsg.contains("template") || errorMsg.contains("Template")) {
                    throw new RuntimeException("DLT Template Error: " + errorMsg + ". Please check if Template ID " + dltTemplateId + " is linked in Fast2SMS dashboard.");
                } else if (errorMsg.contains("balance") || errorMsg.contains("Balance")) {
                    throw new RuntimeException("Insufficient Balance: " + errorMsg + ". Please recharge your Fast2SMS account.");
                } else if (errorMsg.contains("authorization") || errorMsg.contains("API")) {
                    throw new RuntimeException("API Key Error: " + errorMsg + ". Please check your Fast2SMS API key.");
                } else {
                    throw new RuntimeException("SMS sending failed: " + errorMsg);
                }
            }
            
            log.info("SUCCESS: SMS sent successfully to {}", formattedPhone);
        } catch (RuntimeException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (Exception e) {
            log.error("ERROR: Failed to parse SMS response: {}", e.getMessage(), e);
            throw new RuntimeException("SMS sending failed: Invalid response format - " + response.body());
        }
    }
    
    // ✅ Legacy method for backward compatibility (non-DLT)
    public void sendSms(String phone, String message) throws Exception {
        // Format phone number (remove +91 if present)
        String formattedPhone = phone.replace("+91", "").replaceAll("\\s", "");
        
        // Validate phone number
        if (formattedPhone.length() != 10) {
            throw new RuntimeException("Invalid phone number format");
        }
        
        String url = "https://www.fast2sms.com/dev/bulkV2";
        
        // Correct JSON format for Fast2SMS
        String jsonPayload = String.format(
            "{\"route\":\"otp\",\"message\":\"%s\",\"language\":\"english\",\"numbers\":\"%s\"}",
            URLEncoder.encode(message, "UTF-8"), formattedPhone
        );
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("authorization", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        System.out.println("SMS Response: " + response.body());
        
        // Check if response indicates success
        if (response.statusCode() != 200) {
            throw new RuntimeException("SMS sending failed: " + response.body());
        }
        
        // Parse JSON response to check if SMS was actually sent
        JSONObject jsonResponse = new JSONObject(response.body());
        if (!jsonResponse.getBoolean("return")) {
            throw new RuntimeException("SMS sending failed: " + jsonResponse.getString("message"));
        }
    }
}