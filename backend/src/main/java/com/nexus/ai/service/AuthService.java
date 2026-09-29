package com.nexus.ai.service;

import com.nexus.ai.domain.User;
import com.nexus.ai.repository.UserRepository;
import com.nexus.ai.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final QuotaService quotaService;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, QuotaService quotaService, EmailService emailService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.quotaService = quotaService;
        this.emailService = emailService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initAdminUser() {
        try {
            String adminEmail = "saikumarnexus46@gmail.com";
            String adminPassword = "SaiNexus@999";
            String hashedPassword = hashPassword(adminPassword);

            User admin = userRepository.findByEmail(adminEmail).orElse(null);
            if (admin == null) {
                admin = User.builder()
                        .email(adminEmail)
                        .passwordHash(hashedPassword)
                        .name("Admin Sai Kumar")
                        .role("ROLE_ADMIN")
                        .build();
                admin = userRepository.save(admin);
                quotaService.getUsageStats(admin.getId());
                log.info("Admin user [{}] successfully created on startup with ROLE_ADMIN.", adminEmail);
            } else {
                admin.setRole("ROLE_ADMIN");
                admin.setPasswordHash(hashedPassword);
                userRepository.save(admin);
                log.info("Admin user [{}] updated and verified on startup with ROLE_ADMIN.", adminEmail);
            }
        } catch (Exception e) {
            log.error("Failed to seed admin user on startup: {}", e.getMessage(), e);
        }
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

        // Dispatch welcome email asynchronously
        emailService.sendRegistrationNotificationEmail(user.getEmail(), user.getName());

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

        // Dispatch sign-in security alert email asynchronously
        emailService.sendLoginNotificationEmail(user.getEmail(), user.getName(), user.getRole());

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
