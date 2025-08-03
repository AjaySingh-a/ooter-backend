package com.ooter.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Service
public class FileStorageService {

    @Value("${upload.dir}")
    private String uploadDir;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "pdf", "cdr");

    public String saveFile(MultipartFile file) throws IOException {
        // Validate file type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IOException("Invalid file name");
        }

        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new IOException("Unsupported file type. Allowed types: " + ALLOWED_EXTENSIONS);
        }

        // Generate unique filename
        String filename = System.currentTimeMillis() + "_" + originalFilename;
        Path dirPath = Paths.get(uploadDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // Save file
        Path filePath = dirPath.resolve(filename);
        file.transferTo(filePath.toFile());

        // Return local path as dummy URL
        return "/uploads/" + filename;
    }
}