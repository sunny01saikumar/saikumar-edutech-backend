package com.education.sai.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.education.sai.dto.BlogFeedResponse;
import com.education.sai.dto.BlogRequest;
import com.education.sai.model.Blog;
import com.education.sai.repo.BlogRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogRepository blogRepository;
    private final Cloudinary cloudinary;

    public Blog create(BlogRequest request, MultipartFile thumbnail) throws Exception {

        Map uploadResult = cloudinary.uploader().upload(
                thumbnail.getBytes(),
                ObjectUtils.asMap("folder", "blogs")
        );

        String imageUrl = uploadResult.get("secure_url").toString();

        String slug = request.getTitle()
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");

        Document doc = Jsoup.parse(request.getContent());

        doc.select("script").remove();
        doc.select("object").remove();
        doc.select("embed").remove();

        String cleanHtml = doc.body().html();

        Blog blog = Blog.builder()
                .title(request.getTitle())
                .slug(slug)
                .summary(request.getSummary())
                .content(cleanHtml)
                .thumbnail(imageUrl)
                .authorName("Saikumar")
                .createdAt(LocalDateTime.now())
                .build();
        return blogRepository.save(blog);
    }

    public Blog getBySlug(String slug) {
        return blogRepository.findBySlug(slug).orElseThrow();
    }

    public List<BlogFeedResponse> getAllBlogs() {
        return blogRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(blog -> BlogFeedResponse.builder()
                                .id(blog.getId())
                                .title(blog.getTitle())
                                .slug(blog.getSlug())
                                .summary(blog.getSummary())
                                .thumbnail(blog.getThumbnail())
                                .authorName(blog.getAuthorName())
                                .createdAt(blog.getCreatedAt())
                                .content(blog.getContent())
                                .build())
                .toList();
    }
}