package ge.kiu.library.exception;

/** Thrown when a unique value (email, ISBN, category name) already exists -> HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
