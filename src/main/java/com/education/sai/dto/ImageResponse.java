package com.education.sai.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageResponse {

    private Long id;

    private String image;

    private String description;

    private String uploadedBy;

    private String originalName;
}