package com.nexus.ai.service;

import com.nexus.ai.domain.User;
import com.nexus.ai.repository.UserRepository;
import com.nexus.ai.security.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final QuotaService quotaService;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, QuotaService quotaService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.quotaService = quotaService;
    }

    @Transactional
    public Map<String, Object> register(String email, String password, String name) {
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Email and password are required.");
        }

        String cleanEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("User with email " + cleanEmail + " already exists.");
        }

        User user = User.builder()
                .email(cleanEmail)
                .passwordHash(hashPassword(password))
                .name(name != null ? name.trim() : "NEXUS User")
                .role("ROLE_USER")
                .build();

        user = userRepository.save(user);

        // Initialize default free subscription and quota
        quotaService.getUsageStats(user.getId());

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "name", user.getName(),
                        "role", user.getRole()
                )
        );
    }

    public Map<String, Object> login(String email, String password) {
        if (email == null || password == null) {
            throw new IllegalArgumentException("Email and password are required.");
        }

        String cleanEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!user.getPasswordHash().equals(hashPassword(password))) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "name", user.getName(),
                        "role", user.getRole()
                )
        );
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
