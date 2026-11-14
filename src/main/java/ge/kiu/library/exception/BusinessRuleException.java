package ge.kiu.library.exception;

/** Thrown when an operation would break a business rule (e.g. deleting an author who has books) -> HTTP 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
