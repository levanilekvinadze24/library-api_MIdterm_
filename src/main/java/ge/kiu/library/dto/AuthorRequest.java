package ge.kiu.library.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Incoming payload for creating / updating an author (validated with @Valid in the controller). */
public record AuthorRequest(

        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must be at most 100 characters")
        String email,

        @Size(max = 50, message = "Nationality must be at most 50 characters")
        String nationality,

        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        @Size(max = 1000, message = "Biography must be at most 1000 characters")
        String biography
) {
}
