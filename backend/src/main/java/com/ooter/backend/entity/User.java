package com.ooter.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String phone;

    @Column(unique = true)
    private String email;

    @JsonIgnore
    private String password;
    
    private boolean verified = false;
    private boolean onHold = false;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private boolean isVendor = false;

    // ✅ Vendor registration details
    private String companyName;
    private String designation;
    private String mobile;      // Can be optional; may be same as phone
    private String gstin;
    private String pan;
    private String cin;
    private String address;

    @Column(name = "google_id")
    private String googleId;

    @Column(name = "profile_picture")
    private String profilePicture;

    // ✅ Recent Searches
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_recent_searches", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "recent_searches")
    private List<String> recentSearches = new ArrayList<>();

    // ✅ Referral Feature Fields
    private String referralCode;
    private String referredBy;
    private int referralCoins = 0;
    private boolean referralUsed = false;

    // ✅ New Fields for Profile Update
    private String gender;
    private LocalDate dateOfBirth;

    // ✅ OTP fields for email verification
    private String emailOtp;
    private LocalDateTime otpExpiry;
    private boolean emailVerified = false;

    // ✅ Spring Security Methods
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return phone;
    }
    public boolean isVerified() {
        return verified;
    }
 
    public boolean isOnHold() {
        return onHold;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }
}
