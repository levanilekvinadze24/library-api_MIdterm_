package ge.kiu.library.service;

import ge.kiu.library.dto.AuthorRequest;
import ge.kiu.library.dto.AuthorResponse;
import ge.kiu.library.dto.AuthorStatsResponse;
import ge.kiu.library.entity.Author;
import ge.kiu.library.exception.BusinessRuleException;
import ge.kiu.library.exception.DuplicateResourceException;
import ge.kiu.library.exception.InvalidRequestException;
import ge.kiu.library.exception.ResourceNotFoundException;
import ge.kiu.library.repository.AuthorRepository;
import ge.kiu.library.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AuthorService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "firstName", "lastName", "email", "nationality", "birthDate");

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    // Constructor injection
    public AuthorService(AuthorRepository authorRepository, BookRepository bookRepository) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
    }

    /**
     * Paginated + sorted list. Builds the Pageable manually from PageRequest and Sort.
     */
    public Page<AuthorResponse> getAuthors(String search, String nationality,
                                           int page, int size, String sortBy, String direction) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new InvalidRequestException("Cannot sort authors by '" + sortBy + "'. Allowed: " + SORTABLE_FIELDS);
        }
        Sort.Direction sortDirection = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> new InvalidRequestException("Sort direction must be 'asc' or 'desc'"));

        Sort sort = Sort.by(sortDirection, sortBy).and(Sort.by(Sort.Direction.ASC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Author> authors;
        if (StringUtils.hasText(nationality)) {
            authors = authorRepository.findByNationalityIgnoreCase(nationality.trim(), pageable);
        } else if (StringUtils.hasText(search)) {
            String term = search.trim();
            authors = authorRepository
                    .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(term, term, pageable);
        } else {
            authors = authorRepository.findAll(pageable);
        }
        return authors.map(AuthorResponse::from);
    }

    public List<AuthorResponse> getAllAuthorsSorted() {
        return authorRepository.findAll(Sort.by("lastName", "firstName")).stream()
                .map(AuthorResponse::from)
                .toList();
    }

    public AuthorResponse getAuthor(Long id) {
        return AuthorResponse.from(findAuthor(id));
    }

    public AuthorResponse getAuthorByEmail(String email) {
        return authorRepository.findByEmailIgnoreCase(email.trim())
                .map(AuthorResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found with email: " + email));
    }

    public List<AuthorStatsResponse> getStatistics() {
        return authorRepository.findAuthorStatistics();
    }

    @Transactional
    public AuthorResponse createAuthor(AuthorRequest request) {
        if (authorRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Author with email '" + request.email() + "' already exists");
        }
        Author author = new Author();
        apply(author, request);
        return AuthorResponse.from(authorRepository.save(author));
    }

    @Transactional
    public AuthorResponse updateAuthor(Long id, AuthorRequest request) {
        Author author = findAuthor(id);
        if (authorRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new DuplicateResourceException("Author with email '" + request.email() + "' already exists");
        }
        apply(author, request);
        return AuthorResponse.from(author); // dirty checking saves the changes on commit
    }

    @Transactional
    public void deleteAuthor(Long id) {
        Author author = findAuthor(id);
        long books = bookRepository.countByAuthorId(id);
        if (books > 0) {
            throw new BusinessRuleException("Cannot delete author " + author.getFullName()
                    + " because they have " + books + " book(s). Delete or reassign the books first.");
        }
        authorRepository.delete(author);
    }

    /**
     * Bulk price change for all books of an author (uses a @Modifying @Query).
     *
     * @param percent discount in percent, e.g. 10 -> every price * 0.90
     * @return number of updated books
     */
    @Transactional
    public int applyDiscount(Long authorId, int percent) {
        if (!authorRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Author", authorId);
        }
        BigDecimal factor = BigDecimal.valueOf(100 - percent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return bookRepository.updatePriceByAuthor(authorId, factor);
    }

    private Author findAuthor(Long id) {
        return authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id));
    }

    private void apply(Author author, AuthorRequest request) {
        author.setFirstName(request.firstName().trim());
        author.setLastName(request.lastName().trim());
        author.setEmail(request.email().trim().toLowerCase());
        author.setNationality(StringUtils.hasText(request.nationality()) ? request.nationality().trim() : null);
        author.setBirthDate(request.birthDate());
        author.setBiography(request.biography());
    }
}
