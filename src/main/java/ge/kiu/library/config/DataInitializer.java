package ge.kiu.library.config;

import ge.kiu.library.dto.StoredFile;
import ge.kiu.library.entity.Author;
import ge.kiu.library.entity.Book;
import ge.kiu.library.entity.Category;
import ge.kiu.library.entity.Review;
import ge.kiu.library.repository.AuthorRepository;
import ge.kiu.library.repository.BookRepository;
import ge.kiu.library.repository.CategoryRepository;
import ge.kiu.library.service.CoverArtService;
import ge.kiu.library.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Inserts demo data on the first start (only when the tables are empty),
 * so every endpoint can be tried immediately. Disable with app.seed-data=false.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed-data", havingValue = "true", matchIfMissing = true)
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final FileStorageService fileStorageService;
    private final CoverArtService coverArtService;
    private final TransactionTemplate transactionTemplate;

    // Constructor injection
    public DataInitializer(AuthorRepository authorRepository,
                           CategoryRepository categoryRepository,
                           BookRepository bookRepository,
                           FileStorageService fileStorageService,
                           CoverArtService coverArtService,
                           PlatformTransactionManager transactionManager) {
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
        this.fileStorageService = fileStorageService;
        this.coverArtService = coverArtService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Bean
    public CommandLineRunner seedDatabase() {
        return args -> transactionTemplate.executeWithoutResult(status -> {
            if (authorRepository.count() > 0 || bookRepository.count() > 0) {
                log.info("Database already contains data - skipping demo data");
                return;
            }

            Category fantasy = new Category("Fantasy", "Magic, myths and imaginary worlds");
            Category sciFi = new Category("Science Fiction", "Science, technology and the future");
            Category classic = new Category("Classic", "Books that stood the test of time");
            Category dystopian = new Category("Dystopian", "Dark visions of society");
            Category mystery = new Category("Mystery", "Crime and detective stories");
            categoryRepository.saveAll(List.of(fantasy, sciFi, classic, dystopian, mystery));

            Author tolkien = new Author("John", "Tolkien", "tolkien@library.ge", "British",
                    LocalDate.of(1892, 1, 3), "Author of The Hobbit and The Lord of the Rings.");
            Author orwell = new Author("George", "Orwell", "orwell@library.ge", "British",
                    LocalDate.of(1903, 6, 25), "Novelist and essayist, author of 1984.");
            Author rowling = new Author("Joanne", "Rowling", "rowling@library.ge", "British",
                    LocalDate.of(1965, 7, 31), "Creator of the Harry Potter series.");
            Author asimov = new Author("Isaac", "Asimov", "asimov@library.ge", "American",
                    LocalDate.of(1920, 1, 2), "Science fiction writer and biochemist.");
            Author christie = new Author("Agatha", "Christie", "christie@library.ge", "British",
                    LocalDate.of(1890, 9, 15), "The best-selling mystery writer of all time.");
            Author dumbadze = new Author("Nodar", "Dumbadze", "dumbadze@library.ge", "Georgian",
                    LocalDate.of(1928, 7, 14), "Georgian writer known for warm, humorous prose.");
            authorRepository.saveAll(List.of(tolkien, orwell, rowling, asimov, christie, dumbadze));

            Book hobbit = book("The Hobbit", "9780547928227", 1937, 310, "14.99", tolkien, fantasy, classic);
            Book lotr = book("The Lord of the Rings", "9780544003415", 1954, 1178, "29.99", tolkien, fantasy, classic);
            Book nineteen = book("1984", "9780451524935", 1949, 328, "9.99", orwell, dystopian, classic, sciFi);
            Book animalFarm = book("Animal Farm", "9780451526342", 1945, 140, "7.99", orwell, dystopian, classic);
            Book hp1 = book("Harry Potter and the Philosopher's Stone", "9780747532699", 1997, 223, "12.50", rowling, fantasy);
            Book hp2 = book("Harry Potter and the Chamber of Secrets", "9780747538493", 1998, 251, "12.50", rowling, fantasy);
            Book foundation = book("Foundation", "9780553293357", 1951, 255, "8.99", asimov, sciFi, classic);
            Book robot = book("I, Robot", "9780553382563", 1950, 224, "10.99", asimov, sciFi);
            Book orient = book("Murder on the Orient Express", "9780062693662", 1934, 256, "11.99", christie, mystery, classic);
            Book none = book("And Then There Were None", "9780062073488", 1939, 264, "10.49", christie, mystery);
            Book iliko = book("Me, Grandma, Iliko and Illarion", "9789941000011", 1960, 198, "8.50", dumbadze, classic);

            hobbit.addReview(new Review("Nino", "nino@example.com", 5, "A perfect adventure."));
            hobbit.addReview(new Review("Giorgi", null, 4, "Great start to Middle-earth."));
            lotr.addReview(new Review("Luka", "luka@example.com", 5, "Epic in every sense."));
            nineteen.addReview(new Review("Ana", "ana@example.com", 5, "Scary and still relevant."));
            nineteen.addReview(new Review("Dato", null, 4, "Heavy but important."));
            animalFarm.addReview(new Review("Mariam", null, 4, "Short and sharp."));
            hp1.addReview(new Review("Saba", "saba@example.com", 5, "Pure magic."));
            hp1.addReview(new Review("Tamar", null, 3, "Fun, a bit childish for me."));
            foundation.addReview(new Review("Levan", null, 4, "Big ideas about history."));
            orient.addReview(new Review("Eka", "eka@example.com", 5, "Brilliant twist."));
            iliko.addReview(new Review("Irakli", null, 5, "Funny and touching."));

            List<Book> books = List.of(hobbit, lotr, nineteen, animalFarm, hp1, hp2,
                    foundation, robot, orient, none, iliko);
            books.forEach(this::attachGeneratedCover);
            bookRepository.saveAll(books);

            log.info("Demo data inserted: 5 categories, 6 authors, 11 books, 11 reviews");
        });
    }

    /** Generates an original cover image, stores it in uploads/covers and saves its name + path on the book. */
    private void attachGeneratedCover(Book book) {
        try {
            byte[] png = coverArtService.generatePng(book.getTitle(), book.getAuthor().getFullName(), book.getIsbn());
            StoredFile stored = fileStorageService.storeGeneratedCover(png, "demo-" + book.getIsbn() + ".png");
            book.setCoverImageName(stored.fileName());
            book.setCoverImagePath(stored.relativePath());
        } catch (RuntimeException ex) {
            log.warn("Could not generate demo cover for '{}': {}", book.getTitle(), ex.getMessage());
        }
    }

    private static Book book(String title, String isbn, int year, int pages, String price,
                             Author author, Category... categories) {
        Book book = new Book();
        book.setTitle(title);
        book.setIsbn(isbn);
        book.setPublishedYear(year);
        book.setPages(pages);
        book.setPrice(new BigDecimal(price));
        book.setDescription(title + " by " + author.getFullName() + ".");
        book.setAuthor(author);
        for (Category category : categories) {
            book.addCategory(category);
        }
        return book;
    }
}
