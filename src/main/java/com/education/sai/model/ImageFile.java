package com.education.sai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalName;

    private String description;

    private String uploadedBy;

    private LocalDateTime uploadedAt;

    @Lob
    @Column(columnDefinition = "BYTEA")
    private byte[] imageData;
}