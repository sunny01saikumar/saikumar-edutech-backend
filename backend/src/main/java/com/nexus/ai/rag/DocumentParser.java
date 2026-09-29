package com.nexus.ai.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    public String parseContent(InputStream inputStream, String filename, String fileType) {
        String lowerName = (filename != null ? filename : "").toLowerCase();
        
        try {
            if (lowerName.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(fileType)) {
                byte[] bytes = inputStream.readAllBytes();
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document);
                }
            } else if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") || (fileType != null && fileType.startsWith("image/"))) {
                return "[Image Document Attached: " + filename + " (" + fileType + ")] - Image metadata and visual knowledge asset indexed for NEXUS AI analysis.";
            } else {
                // Text, Markdown, CSV, JSON, TXT, Code files
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to parse document content for {}: {}", filename, e.getMessage());
            throw new RuntimeException("Error parsing file " + filename + ": " + e.getMessage(), e);
        }
    }
}
