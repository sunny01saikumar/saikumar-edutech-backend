package com.education.sai.controller;


import com.education.sai.model.User;
import com.education.sai.repo.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping
    public List<User> users() {
        return userRepository.findAll();
    }
}