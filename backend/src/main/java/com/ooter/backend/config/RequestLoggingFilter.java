package com.ooter.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(1) // Run before JwtAuthFilter
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Log all requests, especially auth endpoints
        if (path.contains("/auth/")) {
            log.info("=== INCOMING REQUEST ===");
            log.info("Method: {}", method);
            log.info("Path: {}", path);
            log.info("Query String: {}", request.getQueryString());
            log.info("Content Type: {}", request.getContentType());
            log.info("Content Length: {}", request.getContentLength());
            log.info("Remote Address: {}", request.getRemoteAddr());
            log.info("Headers - Authorization: {}", request.getHeader("Authorization") != null ? "Present" : "Not Present");
        }
        
        filterChain.doFilter(request, response);
    }
}

