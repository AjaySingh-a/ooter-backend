package com.ooter.backend.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import com.ooter.backend.entity.User;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    // Configuration constants
    private static final long EXPIRATION_TIME = 2592000000L; // 30 days (30 * 24 * 60 * 60 * 1000)
    private static final String SECRET_KEY = "ooterappsecretkeyooterappsecretkeyooterapp"; // 32+ chars for HS256
    
    private final Key signingKey = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());

    /**
     * Generates JWT token for a user (supports both normal and Google login)
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());
        
        return buildToken(
            user.getPhone() != null ? user.getPhone() : user.getEmail(),
            claims
        );
    }

    /**
     * Legacy token generation (for backward compatibility)
     */
    public String generateToken(String identifier, String role) {
        return buildToken(identifier, Map.of("role", role));
    }

    private String buildToken(String subject, Map<String, Object> claims) {
        return Jwts.builder()
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validates and parses JWT token
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Extracts user identifier (phone/email) from token
     */
    public String extractUserIdentifier(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * Extracts user role from token
     */
    public String extractRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * Extracts user ID from token
     */
    public Long extractUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /**
     * Validates token integrity and expiration
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}