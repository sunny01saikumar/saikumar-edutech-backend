package com.education.sai.controller;

import com.education.sai.dto.BlogRequest;
import com.education.sai.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/blogs")
@RequiredArgsConstructor
public class BlogController {
    private final BlogService blogService;

    @PostMapping
    public ResponseEntity<?> createBlog(@RequestParam("title") String title, @RequestParam("summary") String summary, @RequestParam("content") String content, @RequestParam("thumbnail") MultipartFile thumbnail) throws Exception {
        BlogRequest request = BlogRequest.builder().title(title).summary(summary).content(content).build();
        return ResponseEntity.ok(blogService.create(request, thumbnail));
    }

    @GetMapping
    public ResponseEntity<?> getAllBlogs() {
        return ResponseEntity.ok(blogService.getAllBlogs());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getBlog(@PathVariable String slug) {
        return ResponseEntity.ok(blogService.getBySlug(slug));
    }
}