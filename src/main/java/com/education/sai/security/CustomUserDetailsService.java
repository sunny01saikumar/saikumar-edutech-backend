package com.education.sai.security;

import com.education.sai.model.User;
import com.education.sai.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email).orElseThrow();
        return org.springframework.security.core.userdetails.User.builder().username(user.getEmail()).password(user.getPassword()).authorities("USER").build();
    }
}