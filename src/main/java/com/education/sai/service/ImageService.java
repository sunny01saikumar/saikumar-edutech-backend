package com.education.sai.service;

import com.education.sai.dto.ImageResponse;
import com.education.sai.model.ImageFile;
import com.education.sai.model.User;
import com.education.sai.repo.ImageRepository;
import com.education.sai.repo.UserRepository;
import com.education.sai.security.AuthUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageService {
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;

    public ImageFile upload(MultipartFile file, String description) throws Exception {
        String email = AuthUtil.getCurrentUserEmail();
        User user = userRepository.findByEmail(email).orElseThrow();
        ImageFile image = ImageFile.builder()
                .originalName(file.getOriginalFilename())
                .description(description)
                .uploadedBy(user.getUsername())
                .imageData(file.getBytes())
                .build();
        return imageRepository.save(image);
    }


    public List<ImageResponse> getAll() {
        return imageRepository.findAll().stream().map(img ->
                ImageResponse.builder()
                        .id(img.getId())
                        .description(img.getDescription())
                        .uploadedBy(img.getUploadedBy())
                        .originalName(img.getOriginalName())
                        .image("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(img.getImageData()))
                        .build()).toList();
    }

}