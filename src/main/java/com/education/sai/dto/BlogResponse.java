package com.education.sai.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BlogResponse {

    private String title;

    private String description;

    private String link;

    private String image;

    private String publishedDate;
}