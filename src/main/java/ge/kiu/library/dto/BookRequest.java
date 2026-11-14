package ge.kiu.library.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

/** Incoming JSON payload for creating / updating a book (bound with @RequestBody + @Valid). */
public record BookRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "ISBN is required")
        @Pattern(regexp = ValidationPatterns.ISBN, message = ValidationPatterns.ISBN_MESSAGE)
        String isbn,

        @NotNull(message = "Published year is required")
        @Min(value = 1450, message = "Published year must be 1450 or later")
        @Max(value = 2100, message = "Published year must be 2100 or earlier")
        Integer publishedYear,

        @Min(value = 1, message = "Pages must be at least 1")
        @Max(value = 20000, message = "Pages must be at most 20000")
        Integer pages,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places")
        BigDecimal price,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotNull(message = "Author id is required")
        @Positive(message = "Author id must be positive")
        Long authorId,

        @Size(max = 10, message = "A book can have at most 10 categories")
        Set<@NotNull @Positive Long> categoryIds
) {
}
