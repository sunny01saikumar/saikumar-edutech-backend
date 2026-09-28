package com.nexus.ai.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final String SECRET = "NEXUS_AI_SECRET_KEY_FOR_SAAS_JWT_TOKEN_SIGNING_2026";

    public String generateToken(String userId, String email, String role) {
        String payload = userId + ":" + email + ":" + role + ":" + System.currentTimeMillis();
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public String extractUserId(String token) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            return parts[0];
        } catch (Exception e) {
            return null;
        }
    }

    public String extractEmail(String token) {
        try {
            if (token == null) return null;
            if (token.startsWith("Bearer ")) token = token.substring(7);
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            return parts.length > 1 ? parts[1] : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            if (token == null) return null;
            if (token.startsWith("Bearer ")) token = token.substring(7);
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            return parts.length > 2 ? parts[2] : null;
        } catch (Exception e) {
            return null;
        }
    }
}
