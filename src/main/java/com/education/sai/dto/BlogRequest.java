package com.education.sai.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BlogRequest {
    private String title;
    private String summary;
    private String content;
}