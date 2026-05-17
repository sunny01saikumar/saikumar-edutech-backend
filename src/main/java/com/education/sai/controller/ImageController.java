package com.education.sai.controller;

import com.education.sai.model.ImageFile;
import com.education.sai.service.ImageService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/upload")
    public ImageFile upload(@RequestParam("file") MultipartFile file, @RequestParam("description") String description) throws Exception {
        return imageService.upload(file, description);
    }

    @GetMapping
    public ResponseEntity<?> getAll(){
        return ResponseEntity.ok(imageService.getAll());
    }
}