package com.ooter.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Slf4j
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
        Path dirPath = resolveUploadDir();
        // Always ensure directory exists (avoid TOCTOU issues)
        Files.createDirectories(dirPath);

        // Save file
        Path filePath = dirPath.resolve(filename);
        log.info("Saving uploaded file to: {}", filePath.toAbsolutePath());
        file.transferTo(filePath.toFile());

        // Return local path as dummy URL
        return "/uploads/" + filename;
    }

    /**
     * Resolve upload directory to a safe, writable absolute path.
     *
     * - If upload.dir is blank -> use system temp dir (/tmp or Windows temp)
     * - If upload.dir is relative -> resolve it under system temp dir
     * - If upload.dir is absolute -> use it as-is
     */
    private Path resolveUploadDir() {
        String configured = uploadDir;
        if (configured == null || configured.trim().isEmpty()) {
            configured = "uploads";
        }

        Path configuredPath = Paths.get(configured).normalize();
        if (configuredPath.isAbsolute()) {
            return configuredPath;
        }

        String tmpDir = System.getProperty("java.io.tmpdir");
        return Paths.get(tmpDir, configuredPath.toString()).normalize();
    }
}