package com.education.sai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    private String originalName;

    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String uploadedBy;

    private LocalDateTime uploadedAt;
}