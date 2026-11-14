package ge.kiu.library;

import ge.kiu.library.entity.Author;
import ge.kiu.library.entity.Book;
import ge.kiu.library.repository.AuthorRepository;
import ge.kiu.library.repository.BookRepository;
import ge.kiu.library.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end tests against an in-memory H2 database filled with the demo data
 * (6 authors, 5 categories, 11 books, 11 reviews). Every test is rolled back.
 */
@SpringBootTest(properties = "app.upload.dir=target/test-uploads")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LibraryApiIntegrationTest {

    // a tiny valid PNG header is enough for the upload test
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private final MockMvc mockMvc;
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;

    // Constructor injection in tests too (enabled by @TestConstructor below)
    LibraryApiIntegrationTest(MockMvc mockMvc, AuthorRepository authorRepository,
                              BookRepository bookRepository, CategoryRepository categoryRepository) {
        this.mockMvc = mockMvc;
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
    }

    // ---------------- pagination & sorting ----------------

    @Test
    void booksArePaginatedAndSorted() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "0").param("size", "3").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.content[0].title").value("The Lord of the Rings"));
    }

    @Test
    void authorsUseExplicitPageRequestAndSort() throws Exception {
        mockMvc.perform(get("/api/authors")
                        .param("page", "0").param("size", "2")
                        .param("sortBy", "birthDate").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].lastName").value("Rowling"))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void booksCanBeFilteredByCategoryAndYear() throws Exception {
        Long mysteryId = categoryRepository.findByNameIgnoreCase("Mystery").orElseThrow().getId();

        mockMvc.perform(get("/api/books").param("categoryId", mysteryId.toString()).param("maxYear", "1935"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Murder on the Orient Express"));
    }

    @Test
    void unsupportedSortFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/books").param("sort", "password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Cannot sort books by 'password'")));
    }

    // ---------------- validation ----------------

    @Test
    void invalidAuthorReturns400WithFieldErrors() throws Exception {
        String body = """
                {"firstName": "", "lastName": "X", "email": "not-an-email"}
                """;
        mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.firstName").exists())
                .andExpect(jsonPath("$.validationErrors.lastName").exists())
                .andExpect(jsonPath("$.validationErrors.email").value("Email must be a valid email address"));
    }

    @Test
    void invalidRequestParamReturns400() throws Exception {
        mockMvc.perform(get("/api/books/top-rated").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.limit").exists());
    }

    // ---------------- CRUD ----------------

    @Test
    void createUpdateAndDeleteAuthor() throws Exception {
        String body = """
                {"firstName": "Mikheil", "lastName": "Javakhishvili", "email": "mikheil@library.ge",
                 "nationality": "Georgian", "birthDate": "1880-11-08"}
                """;
        MvcResult result = mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.fullName").value("Mikheil Javakhishvili"))
                .andReturn();
        Long id = authorRepository.findByEmailIgnoreCase("mikheil@library.ge").orElseThrow().getId();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/authors/" + id);

        // duplicate email -> 409
        mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        String update = """
                {"firstName": "Mikheil", "lastName": "Javakhishvili", "email": "mikheil@library.ge",
                 "nationality": "Georgia"}
                """;
        mockMvc.perform(put("/api/authors/{id}", id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationality").value("Georgia"));

        mockMvc.perform(delete("/api/authors/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/authors/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void authorWithBooksCannotBeDeleted() throws Exception {
        Long orwellId = authorRepository.findByEmailIgnoreCase("orwell@library.ge").orElseThrow().getId();
        mockMvc.perform(delete("/api/authors/{id}", orwellId))
                .andExpect(status().isConflict());
    }

    @Test
    void createBookWithUnknownAuthorReturns404() throws Exception {
        String body = """
                {"title": "Ghost", "isbn": "9781111111111", "publishedYear": 2020, "price": 5.00, "authorId": 9999}
                """;
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void manyToManyCategoriesCanBeChanged() throws Exception {
        Book robot = bookRepository.findByIsbn("9780553382563").orElseThrow();
        Long classicId = categoryRepository.findByNameIgnoreCase("Classic").orElseThrow().getId();

        mockMvc.perform(post("/api/books/{b}/categories/{c}", robot.getId(), classicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[*].name", hasItem("Classic")));

        mockMvc.perform(delete("/api/books/{b}/categories/{c}", robot.getId(), classicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(1));
    }

    // ---------------- custom queries ----------------

    @Test
    void statisticsQueriesWork() throws Exception {
        mockMvc.perform(get("/api/authors/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].bookCount").value(2));

        mockMvc.perform(get("/api/categories/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].categoryName").value("Classic"))
                .andExpect(jsonPath("$[0].bookCount").value(7));

        mockMvc.perform(get("/api/books/top-rated").param("limit", "3").param("minReviews", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].averageRating").value(4.5));
    }

    @Test
    void bulkDiscountUpdatesPrices() throws Exception {
        Long orwellId = authorRepository.findByEmailIgnoreCase("orwell@library.ge").orElseThrow().getId();

        mockMvc.perform(put("/api/authors/{id}/books/discount", orwellId).param("percent", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBooks").value(2));

        mockMvc.perform(get("/api/books/isbn/{isbn}", "9780451524935"))
                .andExpect(jsonPath("$.price").value(8.99));
    }

    @Test
    void derivedQueryEndpointsWork() throws Exception {
        mockMvc.perform(get("/api/books/affordable").param("fromYear", "1940").param("toYear", "1960")
                        .param("maxPrice", "9.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].publishedYear", everyItem(org.hamcrest.Matchers.lessThanOrEqualTo(1960))))
                .andExpect(jsonPath("$[0].title").value("Animal Farm"));

        mockMvc.perform(get("/api/categories/by-name").param("name", "fantasy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fantasy"));
    }

    @Test
    void reviewsCanBeAddedAndAveraged() throws Exception {
        Book none = bookRepository.findByIsbn("9780062073488").orElseThrow();
        String review = """
                {"reviewerName": "Keti", "reviewerEmail": "keti@example.com", "rating": 4, "comment": "Clever!"}
                """;
        mockMvc.perform(post("/api/books/{id}/reviews", none.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookTitle").value("And Then There Were None"));

        mockMvc.perform(get("/api/books/{id}/reviews/average", none.getId()))
                .andExpect(jsonPath("$.averageRating").value(4.0));

        String invalid = """
                {"reviewerName": "K", "rating": 7}
                """;
        mockMvc.perform(post("/api/books/{id}/reviews", none.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.rating").value("Rating must be between 1 and 5"));
    }

    // ---------------- file upload ----------------

    @Test
    void coverImageIsStoredOnDiskAndPathSavedInDatabase() throws Exception {
        Book hobbit = bookRepository.findByIsbn("9780547928227").orElseThrow();
        MockMultipartFile file = new MockMultipartFile("file", "hobbit.png", "image/png", PNG_BYTES);

        mockMvc.perform(multipart("/api/books/{id}/cover", hobbit.getId()).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverImageName", endsWith(".png")))
                .andExpect(jsonPath("$.coverImagePath", startsWith("target/test-uploads/covers/")))
                .andExpect(jsonPath("$.coverImageUrl").value("/api/books/" + hobbit.getId() + "/cover"));

        Book saved = bookRepository.findById(hobbit.getId()).orElseThrow();
        assertThat(saved.getCoverImagePath()).isNotNull();
        assertThat(Files.exists(Path.of(saved.getCoverImagePath()))).isTrue();

        mockMvc.perform(get("/api/books/{id}/cover", hobbit.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG_BYTES));
    }

    @Test
    void nonImageUploadIsRejected() throws Exception {
        Book hobbit = bookRepository.findByIsbn("9780547928227").orElseThrow();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/books/{id}/cover", hobbit.getId()).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Only image files are allowed")));
    }

    @Test
    void bookCanBeCreatedWithCoverUsingModelAttribute() throws Exception {
        Author dumbadze = authorRepository.findByEmailIgnoreCase("dumbadze@library.ge").orElseThrow();
        Long classicId = categoryRepository.findByNameIgnoreCase("Classic").orElseThrow().getId();
        MockMultipartFile cover = new MockMultipartFile("cover", "sun.png", "image/png", PNG_BYTES);

        mockMvc.perform(multipart("/api/books/with-cover").file(cover)
                        .param("title", "I See the Sun")
                        .param("isbn", "9789941000028")
                        .param("publishedYear", "1962")
                        .param("pages", "180")
                        .param("price", "9.50")
                        .param("authorId", dumbadze.getId().toString())
                        .param("categoryIds", classicId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Nodar Dumbadze"))
                .andExpect(jsonPath("$.coverImageName", endsWith(".png")))
                .andExpect(jsonPath("$.categories[0].name").value("Classic"));

        // validation also works for @ModelAttribute
        mockMvc.perform(multipart("/api/books/with-cover").param("title", "").param("isbn", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.isbn").exists())
                .andExpect(jsonPath("$.validationErrors.authorId").exists());
    }

    // ---------------- HTML pages (@Controller) ----------------

    @Test
    void htmlPagesRender() throws Exception {
        mockMvc.perform(get("/books").param("size", "3").param("sort", "price").param("dir", "desc"))
                .andExpect(status().isOk())
                .andExpect(view().name("books"));

        mockMvc.perform(get("/books/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("book-form"));

        Book hobbit = bookRepository.findByIsbn("9780547928227").orElseThrow();
        mockMvc.perform(get("/books/{id}", hobbit.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("book-details"));

        mockMvc.perform(get("/books/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error-page"));
    }

    @Test
    void catalogueFiltersRender() throws Exception {
        Long fantasyId = categoryRepository.findByNameIgnoreCase("Fantasy").orElseThrow().getId();
        mockMvc.perform(get("/books").param("categoryId", fantasyId.toString()).param("authorId", "")
                        .param("minYear", "1990").param("maxYear", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("books"))
                .andExpect(model().attributeExists("authors", "categories", "books"));
    }

    @Test
    void editPageUpdatesBook() throws Exception {
        Book hobbit = bookRepository.findByIsbn("9780547928227").orElseThrow();
        Long authorId = hobbit.getAuthor().getId();

        mockMvc.perform(get("/books/{id}/edit", hobbit.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("book-form"))
                .andExpect(model().attribute("editing", true));

        mockMvc.perform(multipart("/books/{id}/edit", hobbit.getId())
                        .param("title", "The Hobbit (Illustrated)")
                        .param("isbn", "9780547928227")
                        .param("publishedYear", "1937")
                        .param("pages", "310")
                        .param("price", "19.99")
                        .param("authorId", authorId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/books/" + hobbit.getId()));

        mockMvc.perform(get("/api/books/{id}", hobbit.getId()))
                .andExpect(jsonPath("$.title").value("The Hobbit (Illustrated)"))
                .andExpect(jsonPath("$.price").value(19.99));

        // invalid edit -> form is shown again with field errors
        mockMvc.perform(multipart("/books/{id}/edit", hobbit.getId()).param("title", "").param("isbn", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("book-form"))
                .andExpect(model().attributeHasFieldErrors("bookForm", "title", "isbn"));
    }

    @Test
    void reviewFormOnBookPage() throws Exception {
        Book robot = bookRepository.findByIsbn("9780553382563").orElseThrow();

        mockMvc.perform(post("/books/{id}/reviews", robot.getId())
                        .param("reviewerName", "Keti")
                        .param("rating", "5")
                        .param("comment", "Loved the robot stories."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/books/" + robot.getId() + "#reviews"));

        mockMvc.perform(get("/api/books/{id}/reviews/average", robot.getId()))
                .andExpect(jsonPath("$.averageRating").value(5.0));

        mockMvc.perform(post("/books/{id}/reviews", robot.getId()).param("reviewerName", "").param("rating", "9"))
                .andExpect(status().isOk())
                .andExpect(view().name("book-details"))
                .andExpect(model().attributeHasFieldErrors("reviewForm", "reviewerName", "rating"));

        mockMvc.perform(get("/books/{id}", robot.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Loved the robot stories.")));
    }
}
