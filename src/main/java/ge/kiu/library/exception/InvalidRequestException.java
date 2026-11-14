package ge.kiu.library.exception;

/** Thrown for invalid request parameters (e.g. unsupported sort field) -> HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
