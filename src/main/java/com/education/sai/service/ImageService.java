package com.education.sai.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import com.education.sai.dto.ImageResponse;
import com.education.sai.model.ImageFile;
import com.education.sai.model.User;
import com.education.sai.repo.ImageRepository;
import com.education.sai.repo.UserRepository;
import com.education.sai.security.AuthUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final Cloudinary cloudinary;

    public ImageFile upload( MultipartFile file, String description) throws Exception {
        String email = AuthUtil.getCurrentUserEmail();
        User user = userRepository.findByEmail(email).orElseThrow();
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
        String imageUrl = uploadResult.get("secure_url").toString();
        ImageFile image = ImageFile.builder().originalName(file.getOriginalFilename()).description(description)
                          .uploadedBy(user.getUsername()).uploadedAt(LocalDateTime.now()).imageUrl(imageUrl).build();
        return imageRepository.save(image);
    }


    public List<ImageResponse> getAll() {
        return imageRepository.findAll().stream().map(img -> ImageResponse.builder().id(img.getId())
                                .imageUrl(img.getImageUrl())
                                .description(img.getDescription())
                                .uploadedBy(img.getUploadedBy())
                                .originalName(img.getOriginalName())
                                .build()
                )
                .toList();
    }
}