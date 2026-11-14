package ge.kiu.library.controller;

import ge.kiu.library.dto.AuthorRequest;
import ge.kiu.library.dto.AuthorResponse;
import ge.kiu.library.dto.AuthorStatsResponse;
import ge.kiu.library.dto.BookResponse;
import ge.kiu.library.dto.PageResponse;
import ge.kiu.library.service.AuthorService;
import ge.kiu.library.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/authors")
@Tag(name = "Authors", description = "CRUD, search, statistics and bulk price update for authors")
public class AuthorController {

    private final AuthorService authorService;
    private final BookService bookService;

    // Constructor injection
    public AuthorController(AuthorService authorService, BookService bookService) {
        this.authorService = authorService;
        this.bookService = bookService;
    }

    /**
     * Example: GET /api/authors?page=0&size=5&sortBy=lastName&direction=desc&search=gal
     */
    @GetMapping
    @Operation(summary = "List authors with pagination, sorting and optional search / nationality filter")
    public PageResponse<AuthorResponse> getAuthors(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String nationality) {
        return PageResponse.from(
                authorService.getAuthors(search, nationality, page, size, sortBy, direction),
                author -> author);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one author by id")
    public AuthorResponse getAuthor(@PathVariable Long id) {
        return authorService.getAuthor(id);
    }

    @GetMapping("/by-email")
    @Operation(summary = "Find an author by email (method-name query)")
    public AuthorResponse getAuthorByEmail(@RequestParam @Email String email) {
        return authorService.getAuthorByEmail(email);
    }

    @GetMapping("/{id}/books")
    @Operation(summary = "Books of an author, paginated and sorted")
    public PageResponse<BookResponse> getBooksOfAuthor(
            @PathVariable Long id,
            @ParameterObject
            @PageableDefault(size = 10, sort = "publishedYear", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(bookService.getBooksByAuthor(id, pageable), book -> book);
    }

    @GetMapping("/stats")
    @Operation(summary = "Number of books per author (custom JPQL @Query)")
    public List<AuthorStatsResponse> getStatistics() {
        return authorService.getStatistics();
    }

    @PostMapping
    @Operation(summary = "Create an author")
    public ResponseEntity<AuthorResponse> createAuthor(@Valid @RequestBody AuthorRequest request) {
        AuthorResponse created = authorService.createAuthor(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an author")
    public AuthorResponse updateAuthor(@PathVariable Long id, @Valid @RequestBody AuthorRequest request) {
        return authorService.updateAuthor(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an author (only if they have no books)")
    public ResponseEntity<Void> deleteAuthor(@PathVariable Long id) {
        authorService.deleteAuthor(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Example: PUT /api/authors/1/books/discount?percent=10  -> every book of author 1 becomes 10% cheaper.
     */
    @PutMapping("/{id}/books/discount")
    @Operation(summary = "Apply a percentage discount to all books of an author (@Modifying @Query)")
    public Map<String, Object> applyDiscount(@PathVariable Long id,
                                             @RequestParam @Min(1) @Max(90) int percent) {
        int updated = authorService.applyDiscount(id, percent);
        return Map.of("authorId", id, "discountPercent", percent, "updatedBooks", updated);
    }
}
