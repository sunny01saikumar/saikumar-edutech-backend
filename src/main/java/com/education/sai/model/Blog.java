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
public class Blog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String slug;
    @Column(length = 1000)
    private String summary;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;
    private String thumbnail;
    private String authorName;
    private LocalDateTime createdAt;
}