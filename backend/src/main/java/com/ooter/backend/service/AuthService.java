package com.ooter.backend.service;

import com.ooter.backend.config.JwtUtil;
import com.ooter.backend.dto.*;
import com.ooter.backend.entity.Role;
import com.ooter.backend.entity.User;
import com.ooter.backend.repository.UserRepository;
import com.ooter.backend.util.GoogleTokenVerifier;
import com.ooter.backend.exception.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;

    public String signup(SignupRequest request) {
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new AuthException("Phone number already exists");
        }

        String generatedReferral = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        User user = User.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .isVendor(false)
                .referralCode(generatedReferral)
                .referralCoins(0)
                .referralUsed(false)
                .build();

        if (request.getReferredBy() != null && !request.getReferredBy().isBlank()) {
            Optional<User> referrer = userRepository.findByReferralCode(request.getReferredBy());
            if (referrer.isPresent()) {
                user.setReferredBy(request.getReferredBy());
            } else {
                throw new AuthException("Invalid referral code");
            }
        }

        userRepository.save(user);
        return "Signup successful";
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new RuntimeException("Invalid phone number"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String token = jwtUtil.generateToken(user.getPhone(), user.getRole().name());

        return LoginResponse.builder()
                .token(token)
                .role(user.getRole())
                .userId(user.getId())
                .name(user.getName())
                .build();
    }

    public LoginResponse googleLogin(String idToken) {
        try {
            // 1. Verify Google token
            GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(idToken);
            
            // 2. Find or create user
            User user = userRepository.findByEmailOrGoogleId(googleUser.email, googleUser.googleId)
                .orElseGet(() -> createGoogleUser(googleUser));
            
            // 3. Generate JWT token
            String token = jwtUtil.generateToken(user);
            
            // 4. Build response
            return buildLoginResponse(user, token);
            
        } catch (Exception e) {
            throw new AuthException("Google login failed: " + e.getMessage());
        }
    }

    private User createGoogleUser(GoogleTokenVerifier.GoogleUser googleUser) {
        User newUser = User.builder()
            .email(googleUser.email)
            .name(googleUser.name)
            .profilePicture(googleUser.picture)
            .googleId(googleUser.googleId)
            .password(passwordEncoder.encode(UUID.randomUUID().toString())) // Random password
            .role(Role.USER)
            .isVendor(false)
            .referralCode(generateReferralCode())
            .referralCoins(0)
            .referralUsed(false)
            .verified(true) // Google verified users are auto-verified
            .build();
        
        return userRepository.save(newUser);
    }

    private String generateReferralCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private LoginResponse buildLoginResponse(User user, String token) {
        return LoginResponse.builder()
            .token(token)
            .role(user.getRole())
            .userId(user.getId())
            .name(user.getName())
            .profilePicture(user.getProfilePicture())
            .build();
    }
}