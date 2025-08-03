package com.ooter.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class OAuth2Controller {

    @GetMapping("/oauth2/success")
    public void handleGoogleLoginSuccess(HttpServletResponse response) throws IOException {
        // Redirect to frontend with a token (this will be handled in CustomOAuth2SuccessHandler)
        response.sendRedirect("https://ooter-client-url.com/oauth2/success");
    }
}
