package ge.kiu.library.repository;

import ge.kiu.library.dto.CategoryStatsView;
import ge.kiu.library.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // ---------- Method-name (derived) queries ----------

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // ---------- Custom @Query (native SQL) ----------

    /**
     * Number of books in every category (native SQL over the join table).
     * Aliases are quoted so PostgreSQL / H2 keep their camelCase for the projection getters.
     */
    @Query(value = """
            SELECT c.id AS "categoryId", c.name AS "categoryName", COUNT(bc.book_id) AS "bookCount"
            FROM categories c
            LEFT JOIN book_categories bc ON bc.category_id = c.id
            GROUP BY c.id, c.name
            ORDER BY COUNT(bc.book_id) DESC, c.name ASC
            """, nativeQuery = true)
    List<CategoryStatsView> findCategoryStatistics();
}
