package com.ooter.backend.controller;

import com.ooter.backend.config.JwtUtil;
import com.ooter.backend.entity.User;
import com.ooter.backend.repository.UserRepository;
import com.ooter.backend.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @GetMapping("/me")
    @Cacheable(value = "userProfile", key = "#user?.id ?: 'default'", unless = "#result == null || #result.body == null")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        return ResponseEntity.ok(
                new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getPhone(),
                        user.getEmail(),
                        user.getRole().name(),
                        user.getReferralCode(),
                        user.getReferredBy(),
                        user.getReferralCoins(),
                        user.isReferralUsed(),
                        user.getGender(),
                        user.getDateOfBirth(),
                        user.getCompanyName(),
                        user.getGstin(),
                        user.getPan()
                )                        
        );
    }

    @GetMapping("/recent-searches")
    @Cacheable(value = "userSearches", key = "#userPrincipal?.id ?: 'default'", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<String>> getRecentSearches(@AuthenticationPrincipal User userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }
        
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        if (user.getRecentSearches() == null) {
            user.setRecentSearches(new ArrayList<>());
        }
        
        return ResponseEntity.ok(user.getRecentSearches());
    }

    @PostMapping("/recent-searches")
    @CacheEvict(value = "userSearches", key = "#user?.id ?: 'default'")
    public ResponseEntity<Void> addSearch(@RequestBody SearchRequest req, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        User freshUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String keyword = req.getQuery();
        List<String> searches = freshUser.getRecentSearches() != null ? 
                            freshUser.getRecentSearches() : new ArrayList<>();
        
        searches.removeIf(s -> s.equalsIgnoreCase(keyword));
        searches.add(0, keyword);
        if (searches.size() > 5) {
            searches = searches.subList(0, 5);
        }

        freshUser.setRecentSearches(searches);
        userRepository.save(freshUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send-email-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> req, @AuthenticationPrincipal User user) {
        String email = req.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email required");
        }

        String otp = String.valueOf(new Random().nextInt(900000) + 100000);
        user.setEmailOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        emailService.sendOtpEmail(email, otp);
        return ResponseEntity.ok("OTP sent to email");
    }

    @PostMapping("/verify-email-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> req, @AuthenticationPrincipal User user) {
        String otp = req.get("otp");
        if (otp == null || user.getEmailOtp() == null) {
            return ResponseEntity.badRequest().body("OTP missing");
        }
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("OTP expired");
        }
        if (!otp.equals(user.getEmailOtp())) {
            return ResponseEntity.badRequest().body("Invalid OTP");
        }

        user.setEmailVerified(true);
        user.setEmailOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
        return ResponseEntity.ok("Email verified");
    }

    @PutMapping("/update-profile")
    @CacheEvict(value = "userProfile", key = "#user?.id ?: 'default'")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> req, @AuthenticationPrincipal User user) {
        String first = req.get("firstName");
        String last = req.get("lastName");
        String gender = req.get("gender");
        String dobStr = req.get("dob");
        String email = req.get("email");
        String companyName = req.get("companyName");
        String gstin = req.get("gstin");
        String pan = req.get("pan");

        if (first != null) {
            user.setName(first + (last != null ? " " + last : ""));
        }
        if (gender != null) {
            user.setGender(gender);
        }
        if (dobStr != null) {
            user.setDateOfBirth(LocalDate.parse(dobStr));
        }

        if (email != null && !email.equals(user.getEmail())) {
            if (!user.isEmailVerified()) {
                return ResponseEntity.badRequest().body("Email not verified via OTP");
            }
            user.setEmail(email);
            user.setEmailVerified(false);
        }

        if (companyName != null) {
            user.setCompanyName(companyName);
        }
        if (gstin != null) {
            user.setGstin(gstin);
        }    
        if (pan != null) {
            user.setPan(pan);
        }

        userRepository.save(user);
        return ResponseEntity.ok("Profile updated");
    }

    @Data
    static class SearchRequest { 
        private String query; 
    }

    @Data
    @AllArgsConstructor
    static class UserResponse {
        private Long id;
        private String name;
        private String phone;
        private String email;
        private String role;
        private String referralCode;
        private String referredBy;
        private int referralCoins;
        private boolean referralUsed;
        private String gender;
        private LocalDate dateOfBirth;
        private String companyName;
        private String gstin;
        private String pan;
    }
}