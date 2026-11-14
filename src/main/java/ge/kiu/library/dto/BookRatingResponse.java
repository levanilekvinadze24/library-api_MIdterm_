package ge.kiu.library.dto;

/** Result of a custom aggregate @Query (JPQL constructor expression) in BookRepository. */
public record BookRatingResponse(
        Long bookId,
        String title,
        Double averageRating,
        Long reviewCount
) {
}
