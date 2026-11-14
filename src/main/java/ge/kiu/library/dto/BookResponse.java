package ge.kiu.library.dto;

import ge.kiu.library.entity.Book;
import ge.kiu.library.entity.Review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record BookResponse(
        Long id,
        String title,
        String isbn,
        Integer publishedYear,
        Integer pages,
        BigDecimal price,
        String description,
        String coverImageName,
        String coverImagePath,
        String coverImageUrl,
        Long authorId,
        String authorName,
        List<CategoryResponse> categories,
        Double averageRating,
        int reviewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** Average rating as five stars, e.g. 4.5 -> "★★★★★", 3.8 -> "★★★★☆" (used by the HTML page). */
    public String ratingStars() {
        if (averageRating == null) {
            return "";
        }
        int full = (int) Math.round(averageRating);
        return "★".repeat(full) + "☆".repeat(5 - full);
    }

    public static BookResponse from(Book book) {
        List<CategoryResponse> categories = book.getCategories().stream()
                .map(CategoryResponse::from)
                .sorted(Comparator.comparing(CategoryResponse::name))
                .toList();

        Double averageRating = book.getReviews().isEmpty() ? null
                : BigDecimal.valueOf(book.getReviews().stream().mapToInt(Review::getRating).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();

        String coverUrl = book.getCoverImageName() == null ? null : "/api/books/" + book.getId() + "/cover";

        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getIsbn(),
                book.getPublishedYear(),
                book.getPages(),
                book.getPrice(),
                book.getDescription(),
                book.getCoverImageName(),
                book.getCoverImagePath(),
                coverUrl,
                book.getAuthor().getId(),
                book.getAuthor().getFullName(),
                categories,
                averageRating,
                book.getReviews().size(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}
