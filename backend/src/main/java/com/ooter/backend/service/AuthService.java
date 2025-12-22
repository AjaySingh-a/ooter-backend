package com.ooter.backend.service;

import com.ooter.backend.config.JwtUtil;
import com.ooter.backend.dto.*;
import com.ooter.backend.entity.Role;
import com.ooter.backend.entity.User;
import com.ooter.backend.repository.UserRepository;
import com.ooter.backend.util.GoogleTokenVerifier;
import com.ooter.backend.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final Fast2SMS fast2SmsService; // ✅ Fast2SMS service inject karo
    private final CacheManager cacheManager; // ✅ Cache manager for cache eviction
    
    // In-memory storage for OTP
    private final ConcurrentHashMap<String, OtpData> otpStorage = new ConcurrentHashMap<>();

    public LoginResponse signup(SignupRequest request) {
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

        User savedUser = userRepository.save(user);
        
        // ✅ CRITICAL: Evict cache for new user to prevent stale data
        evictUserCache(savedUser);
        
        // ✅ Generate token and return LoginResponse (auto-login after signup)
        String token = jwtUtil.generateToken(savedUser);
        
        return LoginResponse.builder()
                .token(token)
                .role(savedUser.getRole())
                .userId(savedUser.getId())
                .name(savedUser.getName())
                .build();
    }

    public LoginResponse login(LoginRequest request) {
        // Try to find user by phone OR email
        User user = userRepository.findByPhoneOrEmail(request.getIdentifier())
                .orElseThrow(() -> new RuntimeException("Invalid phone number or email"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // ✅ CRITICAL: Evict cache on login to ensure fresh data
        // This prevents stale data when user is deleted and recreated with same email/phone
        evictUserCache(user);

        // ✅ Use new token generation method that includes userId
        String token = jwtUtil.generateToken(user);

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
            
            // ✅ CRITICAL: Evict cache on Google login to ensure fresh data
            evictUserCache(user);
            
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
        
        // ✅ Fast2SMS se SMS bhejo with DLT template
        try {
            fast2SmsService.sendOtpSms(phone, otp);
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

    /**
     * Evict all user-related caches to prevent stale data
     * This is critical when user is deleted and recreated with same email/phone
     */
    private void evictUserCache(User user) {
        if (user == null) return;
        
        // 1. Evict users cache (used by JwtAuthFilter) - by phone/email
        Cache userCache = cacheManager.getCache("users");
        if (userCache != null) {
            // Evict by phone if exists
            if (user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
                userCache.evict(user.getPhone());
            }
            // Evict by email if exists
            if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                userCache.evict(user.getEmail());
            }
        }
        
        // 2. Evict userProfile cache (used by /users/me endpoint) - by userId
        Cache userProfileCache = cacheManager.getCache("userProfile");
        if (userProfileCache != null && user.getId() != null) {
            userProfileCache.evict(user.getId());
        }
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

    // ✅ Forgot Password Method - Phone Based with DLT SMS
    public String forgotPassword(String phone) {
        log.info("=== FORGOT PASSWORD REQUEST ===");
        log.info("Phone number received: {}", phone);
        
        // Validate phone format
        if (phone == null || phone.length() != 10 || !phone.matches("\\d+")) {
            log.error("Invalid phone number format: {}", phone);
            throw new AuthException("Invalid phone number format");
        }
        
        // Find user by phone (verify user exists)
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) {
            log.error("User not found for phone: {}", phone);
            throw new AuthException("No user found with this phone number");
        }
        
        log.info("User found for phone: {}", phone);
        
        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        log.info("Generated OTP for phone {}: {}", phone, otp);
        
        // ✅ Send OTP via SMS using DLT template FIRST (before storing)
        // Only store OTP if SMS is sent successfully
        try {
            log.info("Attempting to send SMS to phone: {}", phone);
            fast2SmsService.sendOtpSms(phone, otp);
            log.info("Password reset OTP SMS sent successfully to phone: {}", phone);
            
            // Store OTP only after SMS is sent successfully
            long expiryTime = System.currentTimeMillis() + 5 * 60 * 1000;
            otpStorage.put("forgot_" + phone, new OtpData(otp, expiryTime));
            log.info("OTP stored in memory for phone: {} (expires in 5 minutes)", phone);
            
            return "Password reset OTP sent to your phone number";
        } catch (Exception e) {
            log.error("Failed to send OTP SMS to phone: {}", phone, e);
            log.error("Error details: {}", e.getMessage());
            // Don't store OTP if SMS fails
            throw new AuthException("Failed to send reset OTP. Please check your phone number and try again. Error: " + e.getMessage());
        }
    }

    // ✅ Verify Forgot Password OTP Method - Phone Based
    public String verifyForgotPasswordOtp(String phone, String otp) {
        log.info("=== OTP VERIFICATION REQUEST ===");
        log.info("Phone: {}, OTP: {}", phone, otp);
        
        // Validate phone format
        if (phone == null || phone.length() != 10 || !phone.matches("\\d+")) {
            log.error("Invalid phone number format: {}", phone);
            throw new AuthException("Invalid phone number format");
        }
        
        // Validate OTP format
        if (otp == null || otp.trim().isEmpty() || !otp.matches("\\d{6}")) {
            log.error("Invalid OTP format: {}", otp);
            throw new AuthException("OTP must be exactly 6 digits");
        }
        
        // Find user by phone
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) {
            log.error("User not found for phone: {}", phone);
            throw new AuthException("No user found with this phone number");
        }

        // Get OTP from storage
        String storageKey = "forgot_" + phone;
        OtpData otpData = otpStorage.get(storageKey);
        
        log.info("OTP Storage Key: {}", storageKey);
        log.info("OTP Data in Storage: {}", otpData != null ? "Found" : "NOT FOUND");
        
        if (otpData == null) {
            log.error("No OTP found in storage for phone: {}", phone);
            throw new AuthException("No OTP found. Please request a new OTP first.");
        }
        
        if (System.currentTimeMillis() > otpData.getExpiryTime()) {
            log.error("OTP expired for phone: {}", phone);
            otpStorage.remove(storageKey);
            throw new AuthException("OTP has expired. Please request a new one.");
        }
        
        // Verify OTP - STRICT comparison
        String storedOtp = otpData.getOtp();
        String inputOtp = otp.trim();
        
        log.info("Comparing OTP - Input: '{}' | Stored: '{}'", inputOtp, storedOtp);
        log.info("OTP Match: {}", storedOtp.equals(inputOtp));
        
        if (!storedOtp.equals(inputOtp)) {
            log.error("OTP verification failed - Input OTP '{}' does not match stored OTP '{}'", inputOtp, storedOtp);
            throw new AuthException("Invalid OTP. Please enter the correct OTP sent to your phone.");
        }
        
        log.info("SUCCESS: OTP verification successful for phone: {}", phone);
        
        // OTP verified successfully - remove it
        otpStorage.remove(storageKey);
        
        return "OTP verified successfully. You can now reset your password.";
    }

    // ✅ Reset Password Method - Phone Based
    public String resetPasswordAfterOtp(String phone, String newPassword) {
        // Validate phone format
        if (phone == null || phone.length() != 10 || !phone.matches("\\d+")) {
            throw new AuthException("Invalid phone number format");
        }
        
        // Find user by phone
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) {
            throw new AuthException("No user found with this phone number");
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
        
        // Evict cache
        evictUserCache(user);
        
        return "Password reset successfully";
    }
}