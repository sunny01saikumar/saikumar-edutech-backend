package com.education.sai.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageResponse {

    private Long id;

    private String imageUrl;

    private String description;

    private String uploadedBy;

    private String originalName;
}