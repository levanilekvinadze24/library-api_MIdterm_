package ge.kiu.library.dto;

/** Shared regular expressions used by validation annotations. */
public final class ValidationPatterns {

    /** ISBN-10 (last char may be X) or ISBN-13 starting with 978/979, digits only. */
    public static final String ISBN = "^(\\d{9}[\\dX]|97[89]\\d{10})$";
    public static final String ISBN_MESSAGE = "ISBN must be 10 digits (last may be X) or 13 digits starting with 978/979, without hyphens";

    private ValidationPatterns() {
    }
}
