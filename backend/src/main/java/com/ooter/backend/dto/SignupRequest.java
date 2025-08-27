package com.ooter.backend.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String name;        // Required
    private String phone;       // Required
    private String email;       // Required
    private String password;    // Required
    private String referredBy;  // Optional
}
