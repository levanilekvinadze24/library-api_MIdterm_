package ge.kiu.library.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(

        @NotBlank(message = "Reviewer name is required")
        @Size(min = 2, max = 60, message = "Reviewer name must be between 2 and 60 characters")
        String reviewerName,

        @Email(message = "Reviewer email must be a valid email address")
        @Size(max = 100, message = "Reviewer email must be at most 100 characters")
        String reviewerEmail,

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @Size(max = 1000, message = "Comment must be at most 1000 characters")
        String comment
) {
}
