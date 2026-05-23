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

    private static final String MEDIUM_RSS = "https://medium.com/feed/@saikumar1508";
    public List<BlogResponse> getBlogs() {
        List<BlogResponse> blogs = new ArrayList<>();
        try {
            URL feedSource = new URL(MEDIUM_RSS);
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(feedSource));
            for (SyndEntry entry : feed.getEntries()) {
                String html = entry.getDescription().getValue();
                Document doc = Jsoup.parse(html);
                String image = doc.select("img").attr("src");
                String text = doc.text();
                blogs.add(BlogResponse.builder().title(entry.getTitle()).description(text)
                                .link(entry.getLink()).image(image)
                                .publishedDate(entry.getPublishedDate().toString())
                                .build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return blogs;
    }
}