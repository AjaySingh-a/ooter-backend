package com.ooter.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
    private String phone;
    private String password;
    private boolean isGoogleLogin;
}
