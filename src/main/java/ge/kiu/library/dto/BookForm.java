package ge.kiu.library.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Form-backing object for multipart/form-data requests (bound with @ModelAttribute + @Valid).
 * Used by POST /api/books/with-cover and by the HTML page /books/new.
 */
public class BookForm {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "ISBN is required")
    @Pattern(regexp = ValidationPatterns.ISBN, message = ValidationPatterns.ISBN_MESSAGE)
    private String isbn;

    @NotNull(message = "Published year is required")
    @Min(value = 1450, message = "Published year must be 1450 or later")
    @Max(value = 2100, message = "Published year must be 2100 or earlier")
    private Integer publishedYear;

    @Min(value = 1, message = "Pages must be at least 1")
    @Max(value = 20000, message = "Pages must be at most 20000")
    private Integer pages;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal price;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotNull(message = "Author is required")
    @Positive(message = "Author id must be positive")
    private Long authorId;

    @Size(max = 10, message = "A book can have at most 10 categories")
    private Set<Long> categoryIds = new LinkedHashSet<>();

    /** Pre-fills the edit form from an existing book. */
    public static BookForm from(BookResponse book) {
        BookForm form = new BookForm();
        form.setTitle(book.title());
        form.setIsbn(book.isbn());
        form.setPublishedYear(book.publishedYear());
        form.setPages(book.pages());
        form.setPrice(book.price());
        form.setDescription(book.description());
        form.setAuthorId(book.authorId());
        Set<Long> ids = new LinkedHashSet<>();
        book.categories().forEach(category -> ids.add(category.id()));
        form.setCategoryIds(ids);
        return form;
    }

    public BookRequest toRequest() {
        return new BookRequest(title, isbn, publishedYear, pages, price, description, authorId, categoryIds);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public Integer getPublishedYear() {
        return publishedYear;
    }

    public void setPublishedYear(Integer publishedYear) {
        this.publishedYear = publishedYear;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Set<Long> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(Set<Long> categoryIds) {
        this.categoryIds = categoryIds;
    }
}
