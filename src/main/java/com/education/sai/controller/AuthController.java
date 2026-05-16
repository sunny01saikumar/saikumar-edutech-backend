package com.education.sai.controller;

import com.education.sai.dto.LoginRequest;
import com.education.sai.dto.LoginResponse;
import com.education.sai.dto.RegisterRequest;
import com.education.sai.model.User;
import com.education.sai.repo.UserRepository;
import com.education.sai.security.AuthUtil;
import com.education.sai.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/register")
    public String register(
            @RequestBody RegisterRequest request
    ) {

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                )
                .build();

        userRepository.save(user);

        return "User Registered";
    }

    @PostMapping("/login")
    public LoginResponse login(
            @RequestBody LoginRequest request
    ) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow();

        boolean matches =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                );

        if (!matches) {
            throw new RuntimeException(
                    "Invalid Credentials"
            );
        }

        String token =
                jwtService.generateToken(
                        user.getEmail()
                );

        return LoginResponse.builder()
                .token(token)
                .build();
    }

    @GetMapping("/me")
    public Optional<User> me() {

        String email =
                AuthUtil.getCurrentUserEmail();

        return userRepository.findByEmail(
                email
        );
    }
}