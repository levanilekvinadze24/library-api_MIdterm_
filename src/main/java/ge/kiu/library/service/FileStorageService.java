package ge.kiu.library.service;

import ge.kiu.library.dto.StoredFile;
import ge.kiu.library.exception.FileStorageException;
import ge.kiu.library.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores uploaded files physically on the local disk (default: ./uploads/covers).
 * Only the generated file name and relative path are saved in the database.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MB

    /** Allowed image extensions mapped to their expected content type. */
    private static final Map<String, String> ALLOWED_IMAGES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    private final Path coversDirectory;
    private final String coversRelativeDir;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        Path configured = Paths.get(uploadDir).resolve("covers");
        this.coversRelativeDir = configured.toString().replace('\\', '/');
        this.coversDirectory = configured.toAbsolutePath().normalize();
        try {
            Files.createDirectories(coversDirectory);
        } catch (IOException ex) {
            throw new FileStorageException("Could not create upload directory: " + coversDirectory, ex);
        }
        log.info("Uploaded cover images are stored in {}", coversDirectory);
    }

    /** Validates and saves an image, returning the generated unique name and its relative path. */
    public StoredFile storeCoverImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Please select a non-empty file to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileStorageException("File is too large (max 5 MB)");
        }

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalName);
        extension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);

        if (!ALLOWED_IMAGES.containsKey(extension)) {
            throw new FileStorageException("Only image files are allowed: " + ALLOWED_IMAGES.keySet());
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new FileStorageException("Invalid content type '" + contentType + "', an image is required");
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path target = coversDirectory.resolve(storedName).normalize();

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + originalName, ex);
        }

        log.info("Stored cover image '{}' as {}", originalName, target);
        return new StoredFile(
                storedName,
                coversRelativeDir + "/" + storedName,
                originalName,
                ALLOWED_IMAGES.get(extension),
                file.getSize()
        );
    }

    /**
     * Saves already-generated image bytes (used for the demo covers) under a fixed file name,
     * so restarting the application overwrites the same files instead of creating new ones.
     */
    public StoredFile storeGeneratedCover(byte[] content, String fileName) {
        Path target = resolveSafely(fileName);
        try {
            Files.write(target, content);
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName, ex);
        }
        return new StoredFile(fileName, coversRelativeDir + "/" + fileName, fileName, "image/png", content.length);
    }

    /** Loads a stored cover image as a Spring Resource (used for downloading / displaying it). */
    public Resource loadCoverImage(String fileName) {
        try {
            Path file = resolveSafely(fileName);
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Cover image file not found: " + fileName);
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new FileStorageException("Could not read file " + fileName, ex);
        }
    }

    /** Deletes a stored cover image; a missing file is not an error. */
    public void deleteCoverImage(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveSafely(fileName));
        } catch (IOException ex) {
            log.warn("Could not delete file {}: {}", fileName, ex.getMessage());
        }
    }

    /** Prevents path traversal such as "../../secret.txt". */
    private Path resolveSafely(String fileName) {
        Path file = coversDirectory.resolve(fileName).normalize();
        if (!file.startsWith(coversDirectory)) {
            throw new FileStorageException("Invalid file path: " + fileName);
        }
        return file;
    }
}
