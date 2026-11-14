package ge.kiu.library.dto;

/** Information about a file that was physically saved to disk by FileStorageService. */
public record StoredFile(
        String fileName,
        String relativePath,
        String originalFileName,
        String contentType,
        long size
) {
}
