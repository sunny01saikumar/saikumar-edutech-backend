package com.nexus.ai.controller;

import com.nexus.ai.tools.ToolSecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/files")
public class FileDownloadController {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadController.class);

    @GetMapping("/download")
    public ResponseEntity<?> downloadFile(@RequestParam("filePath") String filePathStr) {
        if (filePathStr == null || filePathStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: filePath parameter is required.");
        }

        if (ToolSecurityUtils.isPathSensitive(filePathStr)) {
            log.warn("Security Alert: Blocked download request for sensitive path [{}]", filePathStr);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Security Exception: Access to sensitive file is restricted.");
        }

        try {
            Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
            Path path = Paths.get(filePathStr).toAbsolutePath().normalize();

            // Handle Windows relative pathing
            if (!Files.exists(path)) {
                String fileName = Paths.get(filePathStr).getFileName() != null ? Paths.get(filePathStr).getFileName().toString() : "";
                Path relativePath = projectRoot.resolve(fileName).normalize();
                if (Files.exists(relativePath)) {
                    path = relativePath;
                }
            }

            if (!path.startsWith(projectRoot) || !Files.exists(path) || Files.isDirectory(path)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found or invalid: " + filePathStr);
            }

            Resource resource = new FileSystemResource(path.toFile());
            String fileName = path.getFileName().toString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(path.toFile().length())
                    .body(resource);

        } catch (Exception e) {
            log.error("File download error for path [{}]: {}", filePathStr, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error downloading file: " + e.getMessage());
        }
    }

    @GetMapping("/download-zip")
    public ResponseEntity<?> downloadZip(@RequestParam("folderPath") String folderPathStr) {
        if (folderPathStr == null || folderPathStr.trim().isEmpty()) {
            folderPathStr = "./";
        }

        if (ToolSecurityUtils.isPathSensitive(folderPathStr)) {
            log.warn("Security Alert: Blocked zip request for sensitive folder path [{}]", folderPathStr);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Security Exception: Access to sensitive folder is restricted.");
        }

        try {
            Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
            Path folderPath = Paths.get(folderPathStr).toAbsolutePath().normalize();

            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath) || !folderPath.startsWith(projectRoot)) {
                folderPath = projectRoot.resolve(folderPathStr).normalize();
            }

            if (!Files.exists(folderPath) || !folderPath.startsWith(projectRoot)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Folder not found: " + folderPathStr);
            }

            String zipName = folderPath.getFileName() != null ? folderPath.getFileName().toString() + ".zip" : "nexus_project.zip";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try (ZipOutputStream zos = new ZipOutputStream(baos);
                 var stream = Files.walk(folderPath, 5)) {

                Path finalFolderPath = folderPath;
                stream.filter(p -> !p.equals(finalFolderPath))
                        .filter(p -> !ToolSecurityUtils.isPathSensitive(p.toString()))
                        .filter(p -> !p.toString().contains(".git") && !p.toString().contains("target") && !p.toString().contains("node_modules"))
                        .forEach(p -> {
                            try {
                                String entryName = finalFolderPath.relativize(p).toString().replace('\\', '/');
                                if (Files.isDirectory(p)) {
                                    zos.putNextEntry(new ZipEntry(entryName + "/"));
                                    zos.closeEntry();
                                } else {
                                    zos.putNextEntry(new ZipEntry(entryName));
                                    Files.copy(p, zos);
                                    zos.closeEntry();
                                }
                            } catch (Exception e) {
                                log.warn("Error zipping path [{}]: {}", p, e.getMessage());
                            }
                        });
            }

            byte[] zipBytes = baos.toByteArray();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(zipName, StandardCharsets.UTF_8).build());
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(zipBytes.length)
                    .body(zipBytes);

        } catch (Exception e) {
            log.error("Zip download error for folder [{}]: {}", folderPathStr, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating zip archive: " + e.getMessage());
        }
    }
}
