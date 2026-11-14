package ge.kiu.library.repository;

import ge.kiu.library.dto.AuthorStatsResponse;
import ge.kiu.library.entity.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthorRepository extends JpaRepository<Author, Long> {

    // ---------- Method-name (derived) queries ----------

    Optional<Author> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Page<Author> findByNationalityIgnoreCase(String nationality, Pageable pageable);

    Page<Author> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName, Pageable pageable);

    // ---------- Custom @Query (JPQL) ----------

    /** Number of books per author, most productive authors first (constructor expression -> DTO). */
    @Query("""
            SELECT new ge.kiu.library.dto.AuthorStatsResponse(
                       a.id, CONCAT(a.firstName, ' ', a.lastName), COUNT(b))
            FROM Author a
            LEFT JOIN a.books b
            GROUP BY a.id, a.firstName, a.lastName
            ORDER BY COUNT(b) DESC, a.lastName ASC
            """)
    List<AuthorStatsResponse> findAuthorStatistics();
}
