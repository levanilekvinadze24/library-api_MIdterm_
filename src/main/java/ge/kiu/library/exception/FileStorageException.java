package ge.kiu.library.exception;

/** Thrown when an uploaded file is invalid or cannot be stored / read. */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
