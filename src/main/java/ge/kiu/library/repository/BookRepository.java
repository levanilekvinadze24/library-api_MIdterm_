package ge.kiu.library.repository;

import ge.kiu.library.dto.BookRatingResponse;
import ge.kiu.library.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    // ---------- Method-name (derived) queries ----------

    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    long countByAuthorId(Long authorId);

    Page<Book> findByAuthorId(Long authorId, Pageable pageable);

    List<Book> findByPublishedYearBetweenAndPriceLessThanEqualOrderByPriceAsc(
            Integer fromYear, Integer toYear, BigDecimal maxPrice);

    // ---------- Custom @Query (JPQL) ----------

    /**
     * Dynamic search with optional filters + pagination + sorting.
     * A null filter value means "do not filter by this field".
     */
    @Query(value = """
            SELECT DISTINCT b FROM Book b
            LEFT JOIN b.categories c
            WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%'))
              AND (:authorId IS NULL OR b.author.id = :authorId)
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:minYear IS NULL OR b.publishedYear >= :minYear)
              AND (:maxYear IS NULL OR b.publishedYear <= :maxYear)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT b) FROM Book b
            LEFT JOIN b.categories c
            WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%'))
              AND (:authorId IS NULL OR b.author.id = :authorId)
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:minYear IS NULL OR b.publishedYear >= :minYear)
              AND (:maxYear IS NULL OR b.publishedYear <= :maxYear)
            """)
    Page<Book> search(@Param("title") String title,
                      @Param("authorId") Long authorId,
                      @Param("categoryId") Long categoryId,
                      @Param("minYear") Integer minYear,
                      @Param("maxYear") Integer maxYear,
                      Pageable pageable);

    /** Books ranked by average review rating (aggregate + GROUP BY + HAVING). */
    @Query("""
            SELECT new ge.kiu.library.dto.BookRatingResponse(b.id, b.title, AVG(r.rating), COUNT(r))
            FROM Book b
            JOIN b.reviews r
            GROUP BY b.id, b.title
            HAVING COUNT(r) >= :minReviews
            ORDER BY AVG(r.rating) DESC, COUNT(r) DESC
            """)
    List<BookRatingResponse> findTopRated(@Param("minReviews") long minReviews, Pageable pageable);

    /** Bulk update: change the price of all books of one author by a factor (e.g. 0.90 = 10% discount). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Book b SET b.price = ROUND(b.price * :factor, 2) WHERE b.author.id = :authorId")
    int updatePriceByAuthor(@Param("authorId") Long authorId, @Param("factor") BigDecimal factor);
}
