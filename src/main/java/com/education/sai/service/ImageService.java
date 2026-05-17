package com.education.sai.service;

import com.education.sai.model.ImageFile;
import com.education.sai.model.User;
import com.education.sai.repo.ImageRepository;
import com.education.sai.repo.UserRepository;
import com.education.sai.security.AuthUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Value("${app.base-url}")
    private String baseUrl;

    private final ImageRepository
            imageRepository;

    private final UserRepository
            userRepository;


    public ImageFile upload(

            MultipartFile file,

            String description

    ) throws Exception {


        String email =
                AuthUtil.getCurrentUserEmail();

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow();



        String fileName =

                UUID.randomUUID()
                        + "_"
                        + file.getOriginalFilename();


        Path path =
                Paths.get(
                        uploadDir,
                        fileName
                );


        Files.createDirectories(
                path.getParent()
        );


        Files.copy(

                file.getInputStream(),

                path,

                StandardCopyOption
                        .REPLACE_EXISTING
        );


        String imageUrl =

                baseUrl
                        + "/uploads/"
                        + fileName;



        ImageFile image =
                ImageFile.builder()

                        .fileName(fileName)

                        .originalName(
                                file.getOriginalFilename()
                        )

                        // save web path instead of local path
                        .filePath(
                                "/uploads/" + fileName
                        )

                        .description(description)

                        .uploadedBy(
                                user.getUsername()
                        )

                        .uploadedAt(
                                LocalDateTime.now()
                        )

                        .build();

        return imageRepository.save(
                image
        );
    }


    public List<ImageFile> getAll() {

        return imageRepository.findAll();

    }

}