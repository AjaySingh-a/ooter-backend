package com.ooter.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import org.json.JSONObject;

@Component
public class Fast2SMS {
    
    @Value("${fast2sms.api.key}")
    private String apiKey;
    
    @Value("${dlt.template.id}")
    private String dltTemplateId;
    
    // ✅ Method for sending OTP SMS with DLT template
    public void sendOtpSms(String phone, String otp) throws Exception {
        // Format phone number (remove +91 if present)
        String formattedPhone = phone.replace("+91", "").replaceAll("\\s", "");
        
        // Validate phone number
        if (formattedPhone.length() != 10) {
            throw new RuntimeException("Invalid phone number format");
        }
        
        // ✅ DLT Template Content (must match exactly with DLT approved template)
        // Template: "ADBOOK COMMUNICATION PRIVATE LIMITED: Your OOTER verification code is {#VAR#}. This code is valid for 5 minutes. Do not share it with anyone."
        String message = String.format(
            "ADBOOK COMMUNICATION PRIVATE LIMITED: Your OOTER verification code is %s. This code is valid for 5 minutes. Do not share it with anyone.",
            otp
        );
        
        String url = "https://www.fast2sms.com/dev/bulkV2";
        
        // ✅ JSON format with DLT Template ID
        String jsonPayload = String.format(
            "{\"route\":\"otp\",\"message\":\"%s\",\"language\":\"english\",\"numbers\":\"%s\",\"template_id\":\"%s\"}",
            URLEncoder.encode(message, "UTF-8"), 
            formattedPhone,
            dltTemplateId
        );
        
        System.out.println("Sending SMS to: " + formattedPhone);
        System.out.println("Using Template ID: " + dltTemplateId);
        System.out.println("Message: " + message);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("authorization", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        System.out.println("SMS Response Status: " + response.statusCode());
        System.out.println("SMS Response Body: " + response.body());
        
        // Check if response indicates success
        if (response.statusCode() != 200) {
            String errorMsg = "SMS sending failed with status " + response.statusCode() + ": " + response.body();
            System.out.println("ERROR: " + errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        // Parse JSON response to check if SMS was actually sent
        try {
            JSONObject jsonResponse = new JSONObject(response.body());
            boolean success = jsonResponse.getBoolean("return");
            System.out.println("SMS API Return Status: " + success);
            
            if (!success) {
                String errorMsg = jsonResponse.optString("message", "Unknown error");
                System.out.println("ERROR: SMS API returned false. Message: " + errorMsg);
                throw new RuntimeException("SMS sending failed: " + errorMsg);
            }
            
            System.out.println("SUCCESS: SMS sent successfully to " + formattedPhone);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to parse SMS response: " + e.getMessage());
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