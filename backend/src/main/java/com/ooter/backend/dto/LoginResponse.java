package com.ooter.backend.dto;

import com.ooter.backend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private Role role;
    private Long userId;
    private String name;
    private String profilePicture; // Add this field
}