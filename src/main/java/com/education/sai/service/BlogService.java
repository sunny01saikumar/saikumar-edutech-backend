package com.education.sai.service;

import com.education.sai.dto.BlogResponse;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.*;

@Service
public class BlogService {

    private static final Map<String, String> BLOG_IMAGES = Map.ofEntries(
            Map.entry("Garbage Collector", "https://miro.medium.com/v2/resize:fit:1400/1*U1W0g8SJH0vA4jXzF6nS6Q.png"),
            Map.entry("Internal flow of Garbage Collector", "https://miro.medium.com/v2/resize:fit:1400/1*Yzo5WwTH3x8Y9x3mV6xS9A.png"),
            Map.entry("Redis", "https://miro.medium.com/v2/resize:fit:1400/1*0Lh8mQn7xjJwY8b6f3g9sA.png"),
            Map.entry("Redis Data types", "https://miro.medium.com/v2/resize:fit:1400/1*WQ7f0X8gK3X2VxJ6M8P4Pg.png"),
            Map.entry("Streams in Java 8", "https://miro.medium.com/v2/resize:fit:1400/1*2w7V0D5QK7x2mK8sW3y9nQ.png"),
            Map.entry("Stack vs Heap Memory", "https://miro.medium.com/v2/resize:fit:1400/1*6x8Q5M0sD9wJ3V2K4n7L8Q.png"),
            Map.entry("Java interview questions", "https://miro.medium.com/v2/resize:fit:1400/1*q7V9X0M2L5N8K4P6R3T1WQ.png"),
            Map.entry("How the Brain Functions Internally", "https://miro.medium.com/v2/resize:fit:1400/1*c8M4Q9X2W7L3N5P6R1T0YQ.png"),
            Map.entry("How the brain works in individuals", "https://miro.medium.com/v2/resize:fit:1400/1*z6Q2M8X4L9P3N5R1T7W0YQ.png"),
            Map.entry("Is AI bad for brain development?", "https://miro.medium.com/v2/resize:fit:1400/1*x5P8N2M4Q7L3R9T1W6Y0Q.png"));

    public List<BlogResponse> getBlogs() {
        try {
            String url = "https://medium.com/feed/@saikumar1508";
            SyndFeedInput input = new SyndFeedInput();
            XmlReader reader = new XmlReader(new URL(url));
            SyndFeed feed = input.build(reader);
            List<BlogResponse> blogs = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                String html = "";
                if (entry.getDescription() != null) {
                    html = entry.getDescription().getValue();
                }

                Document doc = Jsoup.parse(html);
                String text = doc.text();
                String image = BLOG_IMAGES.getOrDefault(entry.getTitle(), "https://placehold.co/1200x600?text=Sai+EduTech");
                blogs.add(BlogResponse.builder().title(entry.getTitle())
                                .description(text.isEmpty() ? "Read this technical blog on Medium" : text)
                                .image(image)
                                .link(entry.getLink())
                                .publishedDate(entry.getPublishedDate() != null ? entry.getPublishedDate().toString() : "")
                                .build());
            }
            return blogs;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}