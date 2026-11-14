package ge.kiu.library.dto;

/** Result of a custom @Query (JPQL constructor expression) in AuthorRepository. */
public record AuthorStatsResponse(
        Long authorId,
        String authorName,
        Long bookCount
) {
}
