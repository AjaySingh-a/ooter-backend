package com.ooter.backend.config;

import com.ooter.backend.entity.User;
import com.ooter.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            processJwtToken(request);
        } catch (JwtException e) {
            handleJwtException(response, e);
            return;
        }
        
        filterChain.doFilter(request, response);
    }

    private void processJwtToken(HttpServletRequest request) throws JwtException {
        String authHeader = request.getHeader(AUTH_HEADER);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        Claims claims = jwtUtil.parseToken(token);
        
        if (shouldAuthenticate(claims)) {
            authenticateUser(request, claims, token);
        }
    }

    private boolean shouldAuthenticate(Claims claims) {
        return claims.getSubject() != null 
            && SecurityContextHolder.getContext().getAuthentication() == null;
    }

    private void authenticateUser(HttpServletRequest request, Claims claims, String token) {
        String userIdentifier = claims.getSubject();
        String role = claims.get("role", String.class);
        
        findUser(userIdentifier)
            .filter(user -> jwtUtil.isTokenValid(token))
            .ifPresent(user -> setSecurityContext(request, user, role));
    }

    private Optional<User> findUser(String userIdentifier) {
        return userIdentifier.contains("@") 
            ? userRepository.findByEmail(userIdentifier)
            : userRepository.findByPhone(userIdentifier);
    }

    private void setSecurityContext(HttpServletRequest request, User user, String role) {
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role))
            );
        
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private void handleJwtException(HttpServletResponse response, JwtException e) throws IOException {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT: " + e.getMessage());
    }
}