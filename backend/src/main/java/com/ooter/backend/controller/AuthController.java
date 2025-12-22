package com.ooter.backend.controller;

import com.ooter.backend.dto.*;
import com.ooter.backend.exception.AuthException;
import com.ooter.backend.service.AuthService;
import com.ooter.backend.config.JwtUtil;
import com.ooter.backend.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        try {
            LoginResponse response = authService.signup(request);
            return ResponseEntity.ok(response);
        } catch (AuthException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Signup failed", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Server error", "Signup failed due to server error"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        try {
            LoginResponse response = authService.googleLogin(request.getIdToken());
            return ResponseEntity.ok(response);
        } catch (AuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Google authentication failed", e.getMessage()));
        }
    }

    // ✅ NEW OTP ENDPOINTS
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody OtpRequest request) {
        try {
            String message = authService.sendOtp(request.getPhone());
            return ResponseEntity.ok(new SuccessResponse(message));
        } catch (AuthException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("OTP sending failed", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Server error", "Failed to send OTP"));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        try {
            String message = authService.verifyOtp(request.getPhone(), request.getOtp());
            return ResponseEntity.ok(new SuccessResponse(message));
        } catch (AuthException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("OTP verification failed", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Server error", "Failed to verify OTP"));
        }
    }

    // ✅ FORGOT PASSWORD ENDPOINTS - Phone Based with DLT SMS
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        log.info("=== FORGOT PASSWORD API CALL ===");
        log.info("Request received - Phone: {}", request.getPhone());
        
        try {
            String message = authService.forgotPassword(request.getPhone());
            log.info("Forgot password successful for phone: {}", request.getPhone());
            return ResponseEntity.ok(new SuccessResponse(message));
        } catch (AuthException e) {
            log.error("AuthException in forgot password for phone {}: {}", request.getPhone(), e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse("Password reset failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in forgot password for phone {}: {}", request.getPhone(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Server error", "Failed to process password reset request"));
        }
    }

    // ✅ OTP-BASED FORGOT PASSWORD ENDPOINTS - Phone Based
    @PostMapping("/verify-forgot-password-otp")
    public ResponseEntity<?> verifyForgotPasswordOtp(@RequestBody VerifyForgotPasswordOtpRequest request) {
        log.info("=== VERIFY FORGOT PASSWORD OTP API CALL ===");
        log.info("Request received - Phone: {}, OTP: {}", request.getPhone(), request.getOtp());
        
        try {
            String message = authService.verifyForgotPasswordOtp(request.getPhone(), request.getOtp());
            log.info("OTP verification successful for phone: {}", request.getPhone());
            return ResponseEntity.ok(new SuccessResponse(message));
        } catch (AuthException e) {
            log.error("AuthException in OTP verification for phone {}: {}", request.getPhone(), e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse("OTP verification failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in OTP verification for phone {}: {}", request.getPhone(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Server error", "Failed to verify OTP"));
        }
    }

    @PostMapping("/reset-password-after-otp")
    public ResponseEntity<?> resetPasswordAfterOtp(@RequestBody ResetPasswordAfterOtpRequest request) {
        try {
            String message = authService.resetPasswordAfterOtp(request.getPhone(), request.getNewPassword());
            return ResponseEntity.ok(new SuccessResponse(message));
        } catch (AuthException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Password reset failed", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Server error", "Failed to reset password"));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Unauthorized", "User not authenticated"));
            }
            
            String newToken = jwtUtil.generateToken(user);
            return ResponseEntity.ok(LoginResponse.builder()
                .token(newToken)
                .role(user.getRole())
                .userId(user.getId())
                .name(user.getName())
                .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Server error", "Failed to refresh token"));
        }
    }
}

// ✅ DTO Classes (same file mein ya alag file mein rakho)
class OtpRequest {
    private String phone;
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}

class VerifyOtpRequest {
    private String phone;
    private String otp;
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}