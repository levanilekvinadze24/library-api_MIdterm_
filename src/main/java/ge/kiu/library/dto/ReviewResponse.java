package ge.kiu.library.dto;

import ge.kiu.library.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String reviewerName,
        String reviewerEmail,
        Integer rating,
        String comment,
        LocalDateTime createdAt
) {
    /** Rating as five stars, e.g. 4 -> "★★★★☆" (used by the HTML page). */
    public String stars() {
        int value = rating == null ? 0 : rating;
        return "★".repeat(value) + "☆".repeat(5 - value);
    }

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBook().getId(),
                review.getBook().getTitle(),
                review.getReviewerName(),
                review.getReviewerEmail(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
