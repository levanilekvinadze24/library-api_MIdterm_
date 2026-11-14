package ge.kiu.library.dto;

/** Interface-based projection filled by a native @Query in CategoryRepository. */
public interface CategoryStatsView {

    Long getCategoryId();

    String getCategoryName();

    Long getBookCount();
}
