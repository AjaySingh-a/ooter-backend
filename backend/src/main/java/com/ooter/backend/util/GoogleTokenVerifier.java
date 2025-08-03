package com.ooter.backend.util;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GoogleTokenVerifier {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    public GoogleUser verify(String idToken) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken googleIdToken = verifier.verify(idToken);
        if (googleIdToken == null) {
            throw new RuntimeException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        
        return new GoogleUser(
                payload.getEmail(),
                (String) payload.get("name"),
                (String) payload.get("picture"),
                payload.getSubject()
        );
    }

    public static class GoogleUser {
        public final String email;
        public final String name;
        public final String picture;
        public final String googleId;

        public GoogleUser(String email, String name, String picture, String googleId) {
            this.email = email;
            this.name = name;
            this.picture = picture;
            this.googleId = googleId;
        }
    }
}