package com.ooter.backend.security;

import com.ooter.backend.entity.User;
import com.ooter.backend.config.JwtUtil;
import com.ooter.backend.entity.Role;
import com.ooter.backend.repository.UserRepository;
import com.ooter.backend.config.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        DefaultOAuth2User oAuth2User = (DefaultOAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        Optional<User> existingUser = userRepository.findByEmail(email);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
        } else {
            user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setPhone("GOOGLE_" + System.currentTimeMillis()); // dummy phone
            user.setRole(Role.USER);
            userRepository.save(user);
        }

        String jwt = jwtUtil.generateToken(user);

        // ✅ Redirect to frontend with token in query param
        response.sendRedirect("http://localhost:3000/oauth-success?token=" + jwt);
    }
}
