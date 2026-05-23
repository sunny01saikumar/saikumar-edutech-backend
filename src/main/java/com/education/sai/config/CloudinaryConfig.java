package com.education.sai.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Value("${cloud.name}")
    private String name;
    @Value("${cloud.apikey}")
    private String key;
    @Value("${cloud.secret}")
    private String secret;
    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(
                Map.of(
                        "cloud_name", name,
                        "api_key", key,
                        "api_secret", secret
                )
        );
    }
}