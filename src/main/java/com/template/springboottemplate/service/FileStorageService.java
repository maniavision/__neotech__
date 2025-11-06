package com.template.springboottemplate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    @Value("${file.upload-dir}")
    private String uploadDir;

    public String storeFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        try {
            String fileName = UUID.randomUUID().toString() + "-" + file.getOriginalFilename();
            Path targetLocation = Paths.get(uploadDir + fileName);
            log.info("Storing file '{}' as '{}'", originalFilename, targetLocation);
            Files.copy(file.getInputStream(), targetLocation);
            return fileName;
        } catch (IOException ex) {
            log.error("Failed to store file '{}'", originalFilename, ex);
            throw new RuntimeException("Could not store file " + file.getOriginalFilename(), ex);
        }
    }
}
