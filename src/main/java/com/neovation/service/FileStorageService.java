package com.neovation.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Storage storage;

    @Value("${gcs.bucket.name}")
    private String bucketName;

    @Value("${gcs.profile.bucket.name}")
    private String bucketProfileName;

    public FileStorageService(Storage storage) {
        this.storage = storage;
    }

    /**
     * Uploads a file to Google Cloud Storage in a user-specific folder.
     *
     * @param file The file to upload.
     * @param userId The ID of the user, used as the folder name.
     * @return The full GCS blob path (e.g., "123/my-file.pdf").
     */
    public String storeFile(MultipartFile file, Long userId) {
        if (file.isEmpty()) {
            log.warn("Cannot store an empty file.");
            throw new RuntimeException("Cannot store an empty file.");
        }
        if (userId == null) {
            log.error("User ID is null, cannot create GCS folder path.");
            throw new RuntimeException("User ID cannot be null for file storage.");
        }

        String originalFilename = file.getOriginalFilename();
        // Create a unique filename to prevent overwrites
        String uniqueFileName = UUID.randomUUID().toString() + "-" + originalFilename;

        // Construct the full path in GCS: "userId/uniqueFileName"
        String blobPath = userId + "/" + uniqueFileName;

        try {
            BlobId blobId = BlobId.of(bucketName, blobPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();

            log.info("Uploading file '{}' to GCS at gs://{}/{}", originalFilename, bucketName, blobPath);

            // Upload the file
            storage.create(blobInfo, file.getBytes());

            // Return the full path, which will be stored in the database
            return blobPath;

        } catch (IOException ex) {
            log.error("Failed to store file '{}' to GCS", originalFilename, ex);
            throw new RuntimeException("Could not store file " + originalFilename, ex);
        }
    }

    public String uploadFile(MultipartFile file, String folderName, String fileName) throws IOException {
        // 1. Get file extension
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 2. Create a unique blob name to prevent browser caching issues
        // e.g., "123/profile-a1b2c3d4.png"
        String uniqueFileName = fileName + "-" + UUID.randomUUID() + extension;
        String blobName = folderName + "/" + uniqueFileName;

        // 3. Configure the blob
        BlobId blobId = BlobId.of(bucketName, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        // 4. Upload the file
        log.info("Uploading file to GCS: gs://{}/{}", bucketName, blobName);
        storage.create(blobInfo, file.getBytes());

        // 5. Return the public URL
        // This assumes the bucket is public or has "Storage Object Viewer"
        // permission for "allUsers".
        String publicUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, blobName);
        log.info("File uploaded successfully to: {}", publicUrl);
        return publicUrl;
    }

    /**
     * Deletes a file from Google Cloud Storage.
     *
     * @param blobPath The full path to the blob in GCS (e.g., "123/my-file.pdf").
     */
    public void deleteFile(String blobPath) {
        if (blobPath == null || blobPath.isEmpty()) {
            log.warn("Attempted to delete file with null or empty blobPath.");
            return;
        }

        try {
            BlobId blobId = BlobId.of(bucketName, blobPath);
            boolean deleted = storage.delete(blobId);

            if (deleted) {
                log.info("Successfully deleted GCS file: gs://{}/{}", bucketName, blobPath);
            } else {
                log.warn("File not found or already deleted in GCS: gs://{}/{}", bucketName, blobPath);
            }
        } catch (Exception e) {
            // Log the error but do not re-throw.
            // We want to continue even if one file deletion fails.
            log.error("Failed to delete GCS file: gs://{}/{}", bucketName, blobPath, e);
        }
    }

    /**
     * Generates a temporary, signed URL to download a private GCS object.
     *
     * @param blobPath The full GCS path (e.g., "123/my-file.pdf").
     * @return A signed URL string.
     */
    public String generateSignedDownloadUrl(String blobPath) {
        if (blobPath == null || blobPath.isEmpty()) {
            log.warn("Cannot generate download URL for empty blobPath.");
            throw new RuntimeException("Invalid file path.");
        }

        try {
            BlobId blobId = BlobId.of(bucketName, blobPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

            // Generate a URL valid for 15 minutes
            URL url = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.httpMethod(HttpMethod.GET));

            log.info("Generated signed URL for: gs://{}/{}", bucketName, blobPath);
            return url.toString();

        } catch (Exception e) {
            log.error("Failed to generate signed URL for GCS file: gs://{}/{}", bucketName, blobPath, e);
            throw new RuntimeException("Could not generate download URL.", e);
        }
    }
}