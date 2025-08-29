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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final Fast2SMS fast2SmsService; // ✅ Fast2SMS service inject karo
    private final EmailService emailService; // ✅ Email service for password reset
    
    // In-memory storage for OTP
    private final ConcurrentHashMap<String, OtpData> otpStorage = new ConcurrentHashMap<>();

    public String signup(SignupRequest request) {
        // Validate required fields
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new AuthException("Name is required");
        }
        
        if (request.getPhone() == null || request.getPhone().length() != 10 || 
            !request.getPhone().matches("\\d+")) {
            throw new AuthException("Valid 10-digit phone number is required");
        }
        
        if (request.getEmail() == null || request.getEmail().trim().isEmpty() || 
            !request.getEmail().contains("@")) {
            throw new AuthException("Valid email is required");
        }
        
        // Enhanced password validation
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new AuthException("Password must be at least 8 characters");
        }
        
        // Check password complexity
        String password = request.getPassword();
        if (!password.matches(".*[A-Z].*")) {
            throw new AuthException("Password must contain at least one capital letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new AuthException("Password must contain at least one small letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new AuthException("Password must contain at least one numeric character");
        }
        if (!password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
            throw new AuthException("Password must contain at least one special character");
        }
        
        // Check if phone already exists
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new AuthException("Phone number already exists");
        }
        
        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AuthException("Email already exists");
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
        // Try to find user by phone OR email
        User user = userRepository.findByPhoneOrEmail(request.getIdentifier())
                .orElseThrow(() -> new RuntimeException("Invalid phone number or email"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String token = jwtUtil.generateToken(user.getPhone() != null ? user.getPhone() : user.getEmail(), user.getRole().name());

        return LoginResponse.builder()
                .token(token)
                .role(user.getRole())
                .userId(user.getId())
                .name(user.getName())
                .build();
    }

    public LoginResponse googleLogin(String idToken) {
        try {
            GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(idToken);
            
            User user = userRepository.findByEmailOrGoogleId(googleUser.email, googleUser.googleId)
                .orElseGet(() -> createGoogleUser(googleUser));
            
            String token = jwtUtil.generateToken(user);
            
            return buildLoginResponse(user, token);
            
        } catch (Exception e) {
            throw new AuthException("Google login failed: " + e.getMessage());
        }
    }

    // ✅ UPDATED OTP METHOD WITH Fast2SMS
    public String sendOtp(String phone) {
        // Check if phone already registered
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new AuthException("Phone number already registered");
        }
        
        // Validate phone format
        if (phone == null || phone.length() != 10 || !phone.matches("\\d+")) {
            throw new AuthException("Invalid phone number format");
        }
        
        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        // Store OTP with expiry (5 minutes)
        long expiryTime = System.currentTimeMillis() + 5 * 60 * 1000;
        otpStorage.put(phone, new OtpData(otp, expiryTime));
        
        // Log OTP for debugging
        System.out.println("OTP for " + phone + ": " + otp);
        
        // ✅ Fast2SMS se SMS bhejo
        try {
            String message = "Your OTP for Ooter app is: " + otp + ". Valid for 5 minutes.";
            fast2SmsService.sendSms(phone, message);
            return "OTP sent successfully";
        } catch (Exception e) {
            // Log the error but don't throw - use fallback for development
            System.out.println("SMS failed: " + e.getMessage());
            System.out.println("OTP for " + phone + ": " + otp);
            return "OTP sent successfully (fallback)";
        }
    }

    public String verifyOtp(String phone, String otp) {
        OtpData otpData = otpStorage.get(phone);
        
        if (otpData == null) {
            throw new AuthException("OTP not found or expired");
        }
        
        if (System.currentTimeMillis() > otpData.getExpiryTime()) {
            otpStorage.remove(phone);
            throw new AuthException("OTP expired");
        }
        
        if (!otpData.getOtp().equals(otp)) {
            throw new AuthException("Invalid OTP");
        }
        
        // OTP verified successfully
        otpStorage.remove(phone);
        return "OTP verified successfully";
    }

    private User createGoogleUser(GoogleTokenVerifier.GoogleUser googleUser) {
        User newUser = User.builder()
            .email(googleUser.email)
            .name(googleUser.name)
            .profilePicture(googleUser.picture)
            .googleId(googleUser.googleId)
            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
            .role(Role.USER)
            .isVendor(false)
            .referralCode(generateReferralCode())
            .referralCoins(0)
            .referralUsed(false)
            .verified(true)
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
    
    // ✅ OTP Data inner class
    private static class OtpData {
        private final String otp;
        private final long expiryTime;
        
        public OtpData(String otp, long expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
        
        public String getOtp() { return otp; }
        public long getExpiryTime() { return expiryTime; }
    }

    // ✅ Forgot Password Method - OTP Based
    public String forgotPassword(String email) {
        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new AuthException("No user found with this email address");
        }

        User user = userOpt.get();
        
        // Check if email is already verified (reuse existing logic)
        if (!user.isEmailVerified()) {
            throw new AuthException("Please verify your email first before requesting password reset");
        }
        
        // Generate 6-digit OTP (same as profile page)
        String otp = String.valueOf(new Random().nextInt(900000) + 100000);
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(5); // 5 minutes expiry
        
        // Save OTP to user (reuse existing email OTP fields)
        user.setEmailOtp(otp);
        user.setOtpExpiry(expiryTime);
        userRepository.save(user);
        
        // Send OTP email using existing working service
        try {
            System.out.println("Sending OTP to: " + email + " | OTP: " + otp);
            emailService.sendOtpEmail(email, otp);
            System.out.println("OTP email sent successfully");
            return "Password reset OTP sent to your email";
        } catch (Exception e) {
            System.out.println("Failed to send OTP email: " + e.getMessage());
            // Remove the OTP if email fails
            user.setEmailOtp(null);
            user.setOtpExpiry(null);
            userRepository.save(user);
            throw new AuthException("Failed to send reset OTP. Please try again.");
        }
    }

    // ✅ Verify OTP Method
    public String verifyForgotPasswordOtp(String email, String otp) {
        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new AuthException("No user found with this email address");
        }

        User user = userOpt.get();
        
        // Check if OTP exists and not expired
        if (user.getEmailOtp() == null || user.getOtpExpiry() == null) {
            throw new AuthException("No OTP found. Please request a new one.");
        }
        
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            // Clear expired OTP
            user.setEmailOtp(null);
            user.setOtpExpiry(null);
            userRepository.save(user);
            throw new AuthException("OTP has expired. Please request a new one.");
        }
        
        // Verify OTP (exact same logic as profile page)
        System.out.println("Verifying OTP: " + otp + " | Stored OTP: " + user.getEmailOtp());
        if (!otp.equals(user.getEmailOtp())) {
            System.out.println("OTP verification failed - Invalid OTP");
            throw new AuthException("Invalid OTP. Please try again.");
        }
        System.out.println("OTP verification successful");
        
        // OTP verified successfully - clear it
        user.setEmailOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
        
        return "OTP verified successfully. You can now reset your password.";
    }

    // ✅ Reset Password Method - Modified for OTP flow
    public String resetPasswordAfterOtp(String email, String newPassword) {
        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new AuthException("No user found with this email address");
        }

        User user = userOpt.get();
        
        // Validate new password
        if (newPassword == null || newPassword.length() < 8) {
            throw new AuthException("Password must be at least 8 characters");
        }
        
        // Check password complexity
        if (!newPassword.matches(".*[A-Z].*")) {
            throw new AuthException("Password must contain at least one capital letter");
        }
        if (!newPassword.matches(".*[a-z].*")) {
            throw new AuthException("Password must contain at least one small letter");
        }
        if (!newPassword.matches(".*\\d.*")) {
            throw new AuthException("Password must contain at least one numeric character");
        }
        if (!newPassword.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
            throw new AuthException("Password must contain at least one special character");
        }
        
        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        return "Password reset successfully";
    }
}