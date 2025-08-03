package com.ooter.backend.controller;

import com.ooter.backend.config.JwtUtil;
import com.ooter.backend.entity.User;
import com.ooter.backend.repository.UserRepository;
import com.ooter.backend.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    // ✅ 1. GET current logged-in user
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

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

    // ✅ 2. GET recent searches
    @GetMapping("/recent-searches")
    public ResponseEntity<List<String>> getRecentSearches(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        String phone = jwtUtil.extractUserIdentifier(token);
        User user = userRepository.findByPhone(phone).orElse(null);

        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(user.getRecentSearches());
    }

    // ✅ 3. POST recent search
    @PostMapping("/recent-searches")
    public ResponseEntity<Void> addSearch(@RequestBody SearchRequest req, HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        String phone = jwtUtil.extractUserIdentifier(token);
        User user = userRepository.findByPhone(phone).orElse(null);

        if (user == null) return ResponseEntity.status(401).build();

        String keyword = req.getQuery();
        List<String> searches = user.getRecentSearches();
        searches.removeIf(s -> s.equalsIgnoreCase(keyword));
        searches.add(0, keyword);
        if (searches.size() > 5) {
            searches = searches.subList(0, 5);
        }

        user.setRecentSearches(searches);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    // ✅ 4. Send OTP to email (REAL OTP via Gmail)
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

        emailService.sendOtpEmail(email, otp); // ✅ send real email
        return ResponseEntity.ok("OTP sent to email");
    }

    // ✅ 5. Verify OTP
    @PostMapping("/verify-email-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> req, @AuthenticationPrincipal User user) {
        String otp = req.get("otp");

        if (otp == null || user.getEmailOtp() == null) {
            return ResponseEntity.badRequest().body("OTP missing");
        }

        if (user.getOtpExpiry() != null && user.getOtpExpiry().isBefore(LocalDateTime.now())) {
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

    // ✅ 6. Update profile (only if OTP is verified)
    @PutMapping("/update-profile")
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

        if (gender != null) user.setGender(gender);
        if (dobStr != null) user.setDateOfBirth(LocalDate.parse(dobStr));

        if (email != null && !email.equals(user.getEmail())) {
            if (!user.isEmailVerified()) {
                return ResponseEntity.badRequest().body("Email not verified via OTP");
            }
            user.setEmail(email);
            user.setEmailVerified(false); // reset after update
        }
        if (companyName != null) user.setCompanyName(companyName);
        if (gstin != null) user.setGstin(gstin);    
        if (pan != null) user.setPan(pan);

        userRepository.save(user);
        return ResponseEntity.ok("Profile updated");
    }

    // ✅ DTOs
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
        private String companyName; // Added for vendor profile
        private String gstin;
        private String pan;
    }
}