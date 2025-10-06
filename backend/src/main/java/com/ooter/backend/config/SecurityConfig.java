package com.ooter.backend.config;

import com.ooter.backend.security.CustomOAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomOAuth2SuccessHandler customOAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", 
                    "/api/auth/signup",
                    "/api/auth/login",
                    "/api/auth/refresh-token",  // ✅ Allow token refresh
                    "/oauth2/**",              // ✅ Allow full oauth2 flow
                    "/login/",               // ✅ Allow internal login endpoints
                    "/error",                  // ✅ Prevent redirect to HTML error page
                    "/favicon.ico"             // Optional
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/upload/image").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/hoardings/").permitAll()

                .requestMatchers(HttpMethod.POST, "/api/vendors").hasAuthority("ROLE_USER")
                .requestMatchers(HttpMethod.GET, "/api/vendors/dashboard").hasAuthority("ROLE_VENDOR")
                .requestMatchers(HttpMethod.POST, "/api/hoardings").hasAuthority("ROLE_VENDOR")
                .requestMatchers("/api/hoardings/vendor/").hasAuthority("ROLE_VENDOR")

                .requestMatchers("/api/bookings", "/api/bookings/", "/api/users/", "/api/cart/")
                    .hasAnyAuthority("ROLE_USER", "ROLE_VENDOR")
                .requestMatchers(HttpMethod.DELETE, "/api/cart/remove/")
                    .hasAnyAuthority("ROLE_USER", "ROLE_VENDOR")

                .anyRequest().authenticated()
            )

            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(auth -> auth
                    .baseUri("/oauth2/authorize")
                )
                .redirectionEndpoint(redir -> redir
                    .baseUri("/oauth2/callback/*")
                )
                .successHandler(customOAuth2SuccessHandler)
            )

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}