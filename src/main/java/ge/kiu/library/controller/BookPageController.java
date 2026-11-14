package ge.kiu.library.controller;

import ge.kiu.library.dto.BookForm;
import ge.kiu.library.dto.BookResponse;
import ge.kiu.library.dto.ReviewForm;
import ge.kiu.library.exception.DuplicateResourceException;
import ge.kiu.library.exception.FileStorageException;
import ge.kiu.library.exception.InvalidRequestException;
import ge.kiu.library.exception.ResourceNotFoundException;
import ge.kiu.library.service.AuthorService;
import ge.kiu.library.service.BookService;
import ge.kiu.library.service.CategoryService;
import ge.kiu.library.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-rendered HTML pages (Thymeleaf) that demonstrate @Controller, @ModelAttribute,
 * a paginated/sortable/filterable list page and HTML forms with enctype="multipart/form-data".
 */
@Controller
public class BookPageController {

    /** Columns the list page can be sorted by (entity property -> label shown in the UI). */
    private static final Map<String, String> SORT_OPTIONS = new LinkedHashMap<>();

    static {
        SORT_OPTIONS.put("title", "Title");
        SORT_OPTIONS.put("publishedYear", "Year");
        SORT_OPTIONS.put("price", "Price");
        SORT_OPTIONS.put("createdAt", "Date added");
    }

    private final BookService bookService;
    private final AuthorService authorService;
    private final CategoryService categoryService;
    private final ReviewService reviewService;

    // Constructor injection
    public BookPageController(BookService bookService, AuthorService authorService,
                              CategoryService categoryService, ReviewService reviewService) {
        this.bookService = bookService;
        this.authorService = authorService;
        this.categoryService = categoryService;
        this.reviewService = reviewService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/books";
    }

    // ===================== list (pagination + sorting + filters) =====================

    /** Example: /books?page=0&size=8&sort=price&dir=desc&title=war&categoryId=2&authorId=1&minYear=1900&maxYear=2000 */
    @GetMapping("/books")
    public String listBooks(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "8") int size,
                            @RequestParam(defaultValue = "title") String sort,
                            @RequestParam(defaultValue = "asc") String dir,
                            @RequestParam(required = false) String title,
                            @RequestParam(required = false) Long authorId,
                            @RequestParam(required = false) Long categoryId,
                            @RequestParam(required = false) Integer minYear,
                            @RequestParam(required = false) Integer maxYear,
                            Model model) {
        String sortField = SORT_OPTIONS.containsKey(sort) ? sort : "title";
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        int safeSize = Math.min(Math.max(size, 1), 48);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(direction, sortField));

        Page<BookResponse> books = bookService.searchBooks(title, authorId, categoryId, minYear, maxYear, pageRequest);

        model.addAttribute("books", books);
        model.addAttribute("sort", sortField);
        model.addAttribute("dir", direction.name().toLowerCase());
        model.addAttribute("size", safeSize);
        model.addAttribute("title", title == null ? "" : title);
        model.addAttribute("authorId", authorId);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("minYear", minYear);
        model.addAttribute("maxYear", maxYear);
        model.addAttribute("sortOptions", SORT_OPTIONS);
        model.addAttribute("authors", authorService.getAllAuthorsSorted());
        model.addAttribute("categories", categoryService.getAllCategoriesSorted());
        return "books";
    }

    // ===================== details + reviews =====================

    @GetMapping("/books/{id}")
    public String bookDetails(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("reviewForm")) {
            model.addAttribute("reviewForm", new ReviewForm());
        }
        addDetails(id, model);
        return "book-details";
    }

    /** "Write a review" form on the book page (@ModelAttribute + @Valid). */
    @PostMapping("/books/{id}/reviews")
    public String addReview(@PathVariable Long id,
                            @Valid @ModelAttribute("reviewForm") ReviewForm reviewForm,
                            BindingResult bindingResult,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addDetails(id, model);
            return "book-details";
        }
        reviewService.addReview(id, reviewForm.toRequest());
        redirectAttributes.addFlashAttribute("message", "Thanks, your review was added.");
        return "redirect:/books/" + id + "#reviews";
    }

    // ===================== create =====================

    @GetMapping("/books/new")
    public String newBookForm(Model model) {
        model.addAttribute("bookForm", new BookForm());
        addFormOptions(model, null, null);
        return "book-form";
    }

    /** Handles the HTML form (enctype="multipart/form-data"): fields -> @ModelAttribute, image -> MultipartFile. */
    @PostMapping("/books")
    public String createBook(@Valid @ModelAttribute("bookForm") BookForm bookForm,
                             BindingResult bindingResult,
                             @RequestParam(value = "cover", required = false) MultipartFile cover,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                BookResponse created = bookService.createBookWithCover(bookForm.toRequest(), cover);
                redirectAttributes.addFlashAttribute("message", "\"" + created.title() + "\" was added to the catalogue.");
                return "redirect:/books/" + created.id();
            } catch (DuplicateResourceException ex) {
                bindingResult.rejectValue("isbn", "duplicate", ex.getMessage());
            } catch (FileStorageException | ResourceNotFoundException ex) {
                bindingResult.reject("error", ex.getMessage());
            }
        }
        addFormOptions(model, null, null);
        return "book-form";
    }

    // ===================== edit =====================

    @GetMapping("/books/{id}/edit")
    public String editBookForm(@PathVariable Long id, Model model) {
        BookResponse book = bookService.getBook(id);
        model.addAttribute("bookForm", BookForm.from(book));
        addFormOptions(model, id, book.coverImageUrl());
        return "book-form";
    }

    @PostMapping("/books/{id}/edit")
    public String updateBook(@PathVariable Long id,
                             @Valid @ModelAttribute("bookForm") BookForm bookForm,
                             BindingResult bindingResult,
                             @RequestParam(value = "cover", required = false) MultipartFile cover,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                bookService.updateBookWithCover(id, bookForm.toRequest(), cover);
                redirectAttributes.addFlashAttribute("message", "Changes saved.");
                return "redirect:/books/" + id;
            } catch (DuplicateResourceException ex) {
                bindingResult.rejectValue("isbn", "duplicate", ex.getMessage());
            } catch (FileStorageException ex) {
                bindingResult.reject("error", ex.getMessage());
            }
        }
        addFormOptions(model, id, bookService.getBook(id).coverImageUrl());
        return "book-form";
    }

    // ===================== cover upload & delete =====================

    @PostMapping("/books/{id}/cover")
    public String uploadCover(@PathVariable Long id,
                              @RequestParam("file") MultipartFile file,
                              RedirectAttributes redirectAttributes) {
        try {
            bookService.uploadCover(id, file);
            redirectAttributes.addFlashAttribute("message", "New cover uploaded.");
        } catch (FileStorageException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/books/" + id;
    }

    @PostMapping("/books/{id}/delete")
    public String deleteBook(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        BookResponse book = bookService.getBook(id);
        bookService.deleteBook(id);
        redirectAttributes.addFlashAttribute("message", "\"" + book.title() + "\" was removed from the catalogue.");
        return "redirect:/books";
    }

    /** Errors on HTML pages are shown as a page, not as JSON. */
    @ExceptionHandler({ResourceNotFoundException.class, InvalidRequestException.class})
    public ModelAndView handlePageError(RuntimeException ex) {
        ModelAndView mav = new ModelAndView("error-page");
        HttpStatus status = ex instanceof ResourceNotFoundException ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    // ===================== helpers =====================

    private void addDetails(Long id, Model model) {
        model.addAttribute("book", bookService.getBook(id));
        PageRequest newestFirst = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("reviews", reviewService.getReviewsOfBook(id, newestFirst).getContent());
    }

    /** bookId == null means "create" mode, otherwise "edit" mode. */
    private void addFormOptions(Model model, Long bookId, String currentCoverUrl) {
        model.addAttribute("authors", authorService.getAllAuthorsSorted());
        model.addAttribute("categories", categoryService.getAllCategoriesSorted());
        model.addAttribute("editing", bookId != null);
        model.addAttribute("bookId", bookId);
        model.addAttribute("formAction", bookId == null ? "/books" : "/books/" + bookId + "/edit");
        model.addAttribute("currentCoverUrl", currentCoverUrl);
    }
}
