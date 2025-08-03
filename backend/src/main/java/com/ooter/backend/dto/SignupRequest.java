package com.ooter.backend.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String name;
    private String phone;     // ✅ required for login
    private String email;     // ✅ optional (for profile use)
    private String password;
    private String referredBy; // ✅ optional, for referral system
}
