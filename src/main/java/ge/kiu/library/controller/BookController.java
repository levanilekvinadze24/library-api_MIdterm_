package ge.kiu.library.controller;

import ge.kiu.library.dto.BookForm;
import ge.kiu.library.dto.BookRatingResponse;
import ge.kiu.library.dto.BookRequest;
import ge.kiu.library.dto.BookResponse;
import ge.kiu.library.dto.PageResponse;
import ge.kiu.library.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/books")
@Tag(name = "Books", description = "CRUD, search, pagination, categories and cover image upload")
public class BookController {

    private final BookService bookService;

    // Constructor injection
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * Server-side pagination + sorting with Spring's Pageable.
     * Example: GET /api/books?title=harry&categoryId=2&minYear=1990&page=0&size=5&sort=price,desc&sort=title,asc
     */
    @GetMapping
    @Operation(summary = "Search books (all filters optional) with pagination and sorting")
    public PageResponse<BookResponse> getBooks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer minYear,
            @RequestParam(required = false) Integer maxYear,
            @ParameterObject
            @PageableDefault(size = 10, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(
                bookService.searchBooks(title, authorId, categoryId, minYear, maxYear, pageable),
                book -> book);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one book by id")
    public BookResponse getBook(@PathVariable Long id) {
        return bookService.getBook(id);
    }

    @GetMapping("/isbn/{isbn}")
    @Operation(summary = "Get a book by ISBN (method-name query)")
    public BookResponse getBookByIsbn(@PathVariable String isbn) {
        return bookService.getBookByIsbn(isbn);
    }

    @GetMapping("/affordable")
    @Operation(summary = "Books published between two years and cheaper than maxPrice (method-name query)")
    public List<BookResponse> getAffordableBooks(
            @RequestParam(defaultValue = "1450") @Min(1450) int fromYear,
            @RequestParam(defaultValue = "2100") @Max(2100) int toYear,
            @RequestParam @DecimalMin("0.00") BigDecimal maxPrice) {
        return bookService.getAffordableBooks(fromYear, toYear, maxPrice);
    }

    @GetMapping("/top-rated")
    @Operation(summary = "Books ranked by average rating (aggregate @Query with GROUP BY / HAVING)")
    public List<BookRatingResponse> getTopRated(
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "1") @Min(1) long minReviews) {
        return bookService.getTopRatedBooks(limit, minReviews);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a book from a JSON body")
    public ResponseEntity<BookResponse> createBook(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.createBook(request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    /**
     * multipart/form-data: book fields are bound with @ModelAttribute, the image with @RequestParam.
     */
    @PostMapping(value = "/with-cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a book and upload its cover in one multipart/form-data request")
    public ResponseEntity<BookResponse> createBookWithCover(
            @Valid @ModelAttribute BookForm form,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        BookResponse created = bookService.createBookWithCover(form.toRequest(), cover);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/books/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a book")
    public BookResponse updateBook(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.updateBook(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a book (its reviews and cover file are deleted too)")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- file upload ----------------

    /** Upload / replace the cover image. Form field name: "file". */
    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the cover image of a book (multipart/form-data, field 'file')")
    public BookResponse uploadCover(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return bookService.uploadCover(id, file);
    }

    @GetMapping("/{id}/cover")
    @Operation(summary = "Download / display the cover image")
    public ResponseEntity<Resource> getCover(@PathVariable Long id) {
        Resource resource = bookService.loadCover(id);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(resource.getFilename()).build().toString())
                .body(resource);
    }

    @DeleteMapping("/{id}/cover")
    @Operation(summary = "Remove the cover image of a book")
    public BookResponse deleteCover(@PathVariable Long id) {
        return bookService.deleteCover(id);
    }

    // ---------------- many-to-many ----------------

    @PostMapping("/{bookId}/categories/{categoryId}")
    @Operation(summary = "Add a category to a book")
    public BookResponse addCategory(@PathVariable Long bookId, @PathVariable Long categoryId) {
        return bookService.addCategory(bookId, categoryId);
    }

    @DeleteMapping("/{bookId}/categories/{categoryId}")
    @Operation(summary = "Remove a category from a book")
    public BookResponse removeCategory(@PathVariable Long bookId, @PathVariable Long categoryId) {
        return bookService.removeCategory(bookId, categoryId);
    }

    private URI locationOf(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
