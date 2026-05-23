package com.education.sai.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BlogFeedResponse {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String thumbnail;
    private String authorName;
    private LocalDateTime createdAt;
    private String content;
}