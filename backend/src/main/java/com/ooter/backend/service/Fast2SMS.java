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