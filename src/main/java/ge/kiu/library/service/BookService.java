package ge.kiu.library.service;

import ge.kiu.library.dto.BookRatingResponse;
import ge.kiu.library.dto.BookRequest;
import ge.kiu.library.dto.BookResponse;
import ge.kiu.library.dto.StoredFile;
import ge.kiu.library.entity.Author;
import ge.kiu.library.entity.Book;
import ge.kiu.library.entity.Category;
import ge.kiu.library.exception.DuplicateResourceException;
import ge.kiu.library.exception.InvalidRequestException;
import ge.kiu.library.exception.ResourceNotFoundException;
import ge.kiu.library.repository.AuthorRepository;
import ge.kiu.library.repository.BookRepository;
import ge.kiu.library.repository.CategoryRepository;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BookService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "title", "isbn", "publishedYear", "pages", "price", "createdAt", "updatedAt");

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;

    // Constructor injection
    public BookService(BookRepository bookRepository,
                       AuthorRepository authorRepository,
                       CategoryRepository categoryRepository,
                       FileStorageService fileStorageService) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
    }

    // ===================== READ =====================

    /** Filtered search with server-side pagination and sorting (Pageable comes from the controller). */
    public Page<BookResponse> searchBooks(String title, Long authorId, Long categoryId,
                                          Integer minYear, Integer maxYear, Pageable pageable) {
        validateSort(pageable.getSort());
        if (minYear != null && maxYear != null && minYear > maxYear) {
            throw new InvalidRequestException("minYear cannot be greater than maxYear");
        }
        String titleFilter = StringUtils.hasText(title) ? title.trim() : "";
        return bookRepository.search(titleFilter, authorId, categoryId, minYear, maxYear, pageable)
                .map(BookResponse::from);
    }

    /** Books of one author, paginated and sorted (method-name query findByAuthorId). */
    public Page<BookResponse> getBooksByAuthor(Long authorId, Pageable pageable) {
        validateSort(pageable.getSort());
        if (!authorRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Author", authorId);
        }
        return bookRepository.findByAuthorId(authorId, pageable).map(BookResponse::from);
    }

    public BookResponse getBook(Long id) {
        return BookResponse.from(findBook(id));
    }

    public BookResponse getBookByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn)
                .map(BookResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ISBN: " + isbn));
    }

    public List<BookResponse> getAffordableBooks(int fromYear, int toYear, BigDecimal maxPrice) {
        if (fromYear > toYear) {
            throw new InvalidRequestException("fromYear cannot be greater than toYear");
        }
        return bookRepository.findByPublishedYearBetweenAndPriceLessThanEqualOrderByPriceAsc(fromYear, toYear, maxPrice)
                .stream()
                .map(BookResponse::from)
                .toList();
    }

    public List<BookRatingResponse> getTopRatedBooks(int limit, long minReviews) {
        return bookRepository.findTopRated(minReviews, PageRequest.of(0, limit)).stream()
                .map(r -> new BookRatingResponse(r.bookId(), r.title(),
                        BigDecimal.valueOf(r.averageRating()).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                        r.reviewCount()))
                .toList();
    }

    // ===================== CREATE / UPDATE / DELETE =====================

    @Transactional
    public BookResponse createBook(BookRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("Book with ISBN '" + request.isbn() + "' already exists");
        }
        Book book = new Book();
        apply(book, request);
        return BookResponse.from(bookRepository.save(book));
    }

    /** Creates the book and (optionally) stores its cover image in one transaction. */
    @Transactional
    public BookResponse createBookWithCover(BookRequest request, MultipartFile cover) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("Book with ISBN '" + request.isbn() + "' already exists");
        }
        Book book = new Book();
        apply(book, request);
        if (cover != null && !cover.isEmpty()) {
            attachCover(book, cover);
        }
        return BookResponse.from(bookRepository.save(book));
    }

    @Transactional
    public BookResponse updateBook(Long id, BookRequest request) {
        Book book = findBook(id);
        if (bookRepository.existsByIsbnAndIdNot(request.isbn(), id)) {
            throw new DuplicateResourceException("Book with ISBN '" + request.isbn() + "' already exists");
        }
        apply(book, request);
        return BookResponse.from(book); // dirty checking
    }

    /** Updates the book and, if a new image was chosen, replaces its cover — all in one transaction. */
    @Transactional
    public BookResponse updateBookWithCover(Long id, BookRequest request, MultipartFile cover) {
        Book book = findBook(id);
        if (bookRepository.existsByIsbnAndIdNot(request.isbn(), id)) {
            throw new DuplicateResourceException("Book with ISBN '" + request.isbn() + "' already exists");
        }
        apply(book, request);
        if (cover != null && !cover.isEmpty()) {
            String oldCover = book.getCoverImageName();
            attachCover(book, cover);
            afterCommit(() -> fileStorageService.deleteCoverImage(oldCover));
        }
        return BookResponse.from(book);
    }

    @Transactional
    public void deleteBook(Long id) {
        Book book = findBook(id);
        String coverName = book.getCoverImageName();
        book.clearCategories();
        bookRepository.delete(book); // reviews are removed by cascade + orphanRemoval
        afterCommit(() -> fileStorageService.deleteCoverImage(coverName));
    }

    // ===================== MANY-TO-MANY: book <-> category =====================

    @Transactional
    public BookResponse addCategory(Long bookId, Long categoryId) {
        Book book = findBook(bookId);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        book.addCategory(category);
        return BookResponse.from(book);
    }

    @Transactional
    public BookResponse removeCategory(Long bookId, Long categoryId) {
        Book book = findBook(bookId);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        if (!book.getCategories().contains(category)) {
            throw new InvalidRequestException("Book " + bookId + " is not in category " + categoryId);
        }
        book.removeCategory(category);
        return BookResponse.from(book);
    }

    // ===================== FILE UPLOAD =====================

    /** Saves the uploaded image on disk and stores its name + path in the books table. */
    @Transactional
    public BookResponse uploadCover(Long bookId, MultipartFile file) {
        Book book = findBook(bookId);
        String oldCover = book.getCoverImageName();
        attachCover(book, file);
        afterCommit(() -> fileStorageService.deleteCoverImage(oldCover));
        return BookResponse.from(book);
    }

    @Transactional
    public BookResponse deleteCover(Long bookId) {
        Book book = findBook(bookId);
        String oldCover = book.getCoverImageName();
        if (oldCover == null) {
            throw new ResourceNotFoundException("Book " + bookId + " has no cover image");
        }
        book.setCoverImageName(null);
        book.setCoverImagePath(null);
        afterCommit(() -> fileStorageService.deleteCoverImage(oldCover));
        return BookResponse.from(book);
    }

    public Resource loadCover(Long bookId) {
        Book book = findBook(bookId);
        if (book.getCoverImageName() == null) {
            throw new ResourceNotFoundException("Book " + bookId + " has no cover image");
        }
        return fileStorageService.loadCoverImage(book.getCoverImageName());
    }

    // ===================== helpers =====================

    private Book findBook(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
    }

    private void apply(Book book, BookRequest request) {
        Author author = authorRepository.findById(request.authorId())
                .orElseThrow(() -> new ResourceNotFoundException("Author", request.authorId()));

        book.setTitle(request.title().trim());
        book.setIsbn(request.isbn().trim());
        book.setPublishedYear(request.publishedYear());
        book.setPages(request.pages());
        book.setPrice(request.price());
        book.setDescription(request.description());
        book.setAuthor(author);

        book.clearCategories();
        for (Category category : resolveCategories(request.categoryIds())) {
            book.addCategory(category);
        }
    }

    private List<Category> resolveCategories(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Category> categories = categoryRepository.findAllById(ids);
        if (categories.size() != ids.size()) {
            Set<Long> missing = new HashSet<>(ids);
            missing.removeAll(categories.stream().map(Category::getId).collect(Collectors.toSet()));
            throw new ResourceNotFoundException("Categories not found with ids: " + missing);
        }
        return categories;
    }

    /** Stores the file; if the DB transaction later rolls back, the new file is removed again. */
    private void attachCover(Book book, MultipartFile file) {
        StoredFile stored = fileStorageService.storeCoverImage(file);
        book.setCoverImageName(stored.fileName());
        book.setCoverImagePath(stored.relativePath());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        fileStorageService.deleteCoverImage(stored.fileName());
                    }
                }
            });
        }
    }

    /** Runs an action (e.g. deleting an old file) only after the transaction committed successfully. */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort books by '" + order.getProperty()
                        + "'. Allowed: " + SORTABLE_FIELDS);
            }
        }
    }
}
