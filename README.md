# Library Management API

Spring Boot final project — a backend for a library with **Authors, Books, Categories and Reviews**.
It demonstrates a layered architecture, Spring Data JPA relationships and queries, transactions,
validation, file upload, and server-side pagination & sorting.

**Stack:** Java 21+ (tested setup: JDK 23) · Spring Boot 3.5 · Spring Data JPA (Hibernate) · PostgreSQL (H2 for quick runs/tests) · Bean Validation · Thymeleaf · Swagger UI · Maven

---

## 1. How to run

### Option A — PostgreSQL (main database)

1. Create the database (pgAdmin → *Create → Database* → `library_db`) **or** start it with Docker:
   ```bash
   docker compose up -d
   ```
2. Default credentials are `postgres` / `postgres`. If yours differ, set environment variables
   `DB_USERNAME`, `DB_PASSWORD` (and `DB_URL` if needed) or edit `src/main/resources/application.yml`.
3. Run:
   ```bash
   mvnw.cmd spring-boot:run          # Windows
   ./mvnw spring-boot:run            # macOS / Linux
   ```
   Or open the folder in IntelliJ IDEA and run `LibraryApiApplication`.

Tables are created automatically (`ddl-auto: update`) and demo data is inserted on the first start.

### Option B — H2 in-memory (no database installation needed)

```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2
```
In IntelliJ: *Run configuration → Active profiles:* `h2`. H2 console: http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:mem:library_db`, user `sa`, empty password).

### Useful URLs

| What | URL |
|---|---|
| Swagger UI (try every endpoint) | http://localhost:8080/swagger-ui.html |
| HTML page: paginated & sortable book list | http://localhost:8080/books |
| HTML page: add book with cover (multipart form) | http://localhost:8080/books/new |
| HTML page: book details, edit, reviews | http://localhost:8080/books/1 |
| REST API | http://localhost:8080/api/... |

A ready **Postman collection** with all 44 requests is in `postman/Library-API.postman_collection.json`.

### One-click scripts (Windows)

- `verify-build.cmd` – downloads Maven + libraries and runs all tests (output in `build-log.txt`)
- `run-app.cmd` – starts the app with the H2 database, then open http://localhost:8080/books

### Tests

```bash
mvnw.cmd test
```
21 integration tests (MockMvc + H2) cover pagination/sorting, validation errors, CRUD, the custom
queries, many-to-many changes, the bulk update, file upload and the HTML pages.

---

## 2. Data model

```
 Author 1 ────< * Book * >──── * Category
                   1
                   │
                   *
                 Review
```

| Entity | Table | Relationships |
|---|---|---|
| `Author` | `authors` | `@OneToMany(mappedBy = "author")` → books |
| `Book` | `books` | `@ManyToOne` → author · `@ManyToMany` + `@JoinTable(book_categories)` → categories · `@OneToMany(cascade = ALL, orphanRemoval = true)` → reviews |
| `Category` | `categories` | `@ManyToMany(mappedBy = "categories")` → books |
| `Review` | `reviews` | `@ManyToOne` → book |

The uploaded cover's file name and relative path are stored in `books.cover_image_name` and
`books.cover_image_path`; the file itself is saved physically in `uploads/covers/`.
The demo books get original cover images generated on start-up by `CoverArtService`
(saved as `uploads/covers/demo-<isbn>.png`, paths stored in the database the same way).

---

## 3. Requirement checklist

| Requirement | Where |
|---|---|
| **Layered packages** | `controller`, `service`, `repository`, `entity`, plus `dto`, `config`, `exception` under `src/main/java/ge/kiu/library` |
| **Stereotype annotations** | `@RestController` (4 REST controllers), `@Controller` (`BookPageController`), `@Service` (6 services), `@Repository` (4 repositories), `@Configuration` (`WebConfig`, `OpenApiConfig`, `DataInitializer`), `@RestControllerAdvice` (`GlobalExceptionHandler`) |
| **Constructor injection only** | Every controller/service/config receives dependencies through its constructor — there is no `@Autowired` field in the project (tests use constructor injection too) |
| **≥ 3 entities** | 4 entities: `Author`, `Book`, `Category`, `Review` |
| **2 relationship types** | `@OneToMany`/`@ManyToOne` (Author–Book, Book–Review) and `@ManyToMany` (Book–Category) |
| **Repositories extend JpaRepository** | `AuthorRepository`, `BookRepository`, `CategoryRepository`, `ReviewRepository` |
| **≥ 2 method-name queries** | e.g. `findByEmailIgnoreCase`, `findByNationalityIgnoreCase`, `findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase`, `findByAuthorId`, `findByPublishedYearBetweenAndPriceLessThanEqualOrderByPriceAsc`, `findByBookIdAndRatingGreaterThanEqualOrderByCreatedAtDesc`, `existsByIsbnAndIdNot` … |
| **≥ 2 custom `@Query`** | `BookRepository.search` (dynamic filters + paging, with `countQuery`), `BookRepository.findTopRated` (`AVG`/`GROUP BY`/`HAVING` → DTO), `BookRepository.updatePriceByAuthor` (`@Modifying` bulk update), `AuthorRepository.findAuthorStatistics` (JPQL constructor expression), `CategoryRepository.findCategoryStatistics` (native SQL → projection), `ReviewRepository.findAverageRatingByBookId` |
| **`@Transactional`** | Every service class is `@Transactional(readOnly = true)`; every method that changes data (`create*`, `update*`, `delete*`, `uploadCover`, `applyDiscount`, `addCategory` …) is annotated `@Transactional` |
| **HTTP method annotations** | `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` in all REST controllers |
| **`@PathVariable`** | `/api/books/{id}`, `/api/books/{bookId}/categories/{categoryId}`, `/api/books/isbn/{isbn}` … |
| **`@RequestParam`** | filters (`title`, `authorId`, `minYear` …), paging params in `AuthorController`, `percent`, and the uploaded `MultipartFile` (`@RequestParam("file")`) |
| **`@RequestBody` / `@ModelAttribute`** | JSON bodies via `@Valid @RequestBody` (`AuthorRequest`, `BookRequest`, `CategoryRequest`, `ReviewRequest`); multipart form via `@Valid @ModelAttribute BookForm` (`POST /api/books/with-cover`, the "Add a book" and "Edit book" pages) and `@Valid @ModelAttribute ReviewForm` (review form on the book page) |
| **JSR-303 validation + `@Valid`** | DTOs use `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Email`, `@Past`, `@Pattern`, `@Positive`, `@DecimalMin`, `@Digits`; request params use `@Min`/`@Max`/`@Email`. Errors return HTTP 400 with a field → message map |
| **File upload (`MultipartFile`, `multipart/form-data`)** | `POST /api/books/{id}/cover`, `POST /api/books/with-cover`, HTML forms with `enctype="multipart/form-data"` in `templates/book-form.html` and `templates/book-details.html`. `FileStorageService` validates type/size, stores the file in `uploads/covers/` with a unique name, and the name + path are saved in the `books` table |
| **Pagination & sorting** | `Pageable` + `@PageableDefault` on `GET /api/books`, `/api/categories`, `/api/books/{id}/reviews`, `/api/authors/{id}/books`; explicit `PageRequest.of(page, size, Sort.by(...))` in `AuthorService.getAuthors` and `BookPageController` (HTML page with page links and sort controls) |

---

## 4. REST endpoints

### Authors — `/api/authors`
| Method | Path | Description |
|---|---|---|
| GET | `/api/authors?page=0&size=10&sortBy=lastName&direction=asc&search=&nationality=` | paginated, sorted, filtered list |
| GET | `/api/authors/{id}` | one author |
| GET | `/api/authors/by-email?email=` | find by email |
| GET | `/api/authors/{id}/books?page=0&size=5&sort=publishedYear,desc` | books of an author |
| GET | `/api/authors/stats` | number of books per author |
| POST | `/api/authors` | create (JSON) |
| PUT | `/api/authors/{id}` | update |
| DELETE | `/api/authors/{id}` | delete (409 if the author still has books) |
| PUT | `/api/authors/{id}/books/discount?percent=10` | bulk price update |

### Books — `/api/books`
| Method | Path | Description |
|---|---|---|
| GET | `/api/books?title=&authorId=&categoryId=&minYear=&maxYear=&page=0&size=10&sort=price,desc` | search + pagination + sorting |
| GET | `/api/books/{id}` · `/api/books/isbn/{isbn}` | one book |
| GET | `/api/books/affordable?fromYear=&toYear=&maxPrice=` | derived query |
| GET | `/api/books/top-rated?limit=5&minReviews=1` | ranking by average rating |
| POST | `/api/books` | create (JSON) |
| POST | `/api/books/with-cover` | create + upload cover (multipart/form-data) |
| PUT | `/api/books/{id}` | update |
| DELETE | `/api/books/{id}` | delete (reviews and cover file too) |
| POST | `/api/books/{id}/cover` | upload / replace cover (multipart field `file`) |
| GET | `/api/books/{id}/cover` | view the cover image |
| DELETE | `/api/books/{id}/cover` | remove the cover |
| POST / DELETE | `/api/books/{bookId}/categories/{categoryId}` | add / remove a category |

### Categories — `/api/categories`
GET (paged) · GET `/{id}` · GET `/by-name?name=` · GET `/stats` · POST · PUT `/{id}` · DELETE `/{id}`

### Reviews
GET `/api/books/{bookId}/reviews` (paged) · GET `/api/books/{bookId}/reviews/positive?minRating=4` ·
GET `/api/books/{bookId}/reviews/average` · POST `/api/books/{bookId}/reviews` ·
GET / PUT / DELETE `/api/reviews/{id}`

### Error format
```json
{
  "timestamp": "2026-11-12T10:15:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/authors",
  "validationErrors": { "email": "Email must be a valid email address" }
}
```
Status codes: 400 validation / bad parameters / bad file, 404 not found, 409 duplicates & business rules,
413 file too large, 415 wrong content type, 500 unexpected.

---

## 5. Project structure

```
src/main/java/ge/kiu/library
├── LibraryApiApplication.java
├── config/       WebConfig, OpenApiConfig, DataInitializer
├── controller/   AuthorController, BookController, CategoryController, ReviewController, BookPageController
├── dto/          request/response records, BookForm, PageResponse, ErrorResponse, projections
├── entity/       Author, Book, Category, Review
├── exception/    custom exceptions + GlobalExceptionHandler
├── repository/   Spring Data JPA repositories
└── service/      AuthorService, BookService, CategoryService, ReviewService, FileStorageService, CoverArtService
src/main/resources
├── application.yml          (PostgreSQL default + "h2" profile)
├── templates/               Thymeleaf pages
└── static/css/style.css
```
