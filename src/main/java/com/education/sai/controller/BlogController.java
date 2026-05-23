package com.education.sai.controller;

import com.education.sai.service.BlogService;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blogs")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;

    @GetMapping
    public Object getBlogs() {
        return blogService.getBlogs();
    }
}