package ge.kiu.library.service;

import ge.kiu.library.dto.ReviewRequest;
import ge.kiu.library.dto.ReviewResponse;
import ge.kiu.library.entity.Book;
import ge.kiu.library.entity.Review;
import ge.kiu.library.exception.InvalidRequestException;
import ge.kiu.library.exception.ResourceNotFoundException;
import ge.kiu.library.repository.BookRepository;
import ge.kiu.library.repository.ReviewRepository;
import org.springframework.data.domain.Page;
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
public class ReviewService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "rating", "createdAt", "reviewerName");

    private final ReviewRepository reviewRepository;
    private final BookRepository bookRepository;

    public ReviewService(ReviewRepository reviewRepository, BookRepository bookRepository) {
        this.reviewRepository = reviewRepository;
        this.bookRepository = bookRepository;
    }

    public Page<ReviewResponse> getReviewsOfBook(Long bookId, Pageable pageable) {
        requireBook(bookId);
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort reviews by '" + order.getProperty()
                        + "'. Allowed: " + SORTABLE_FIELDS);
            }
        }
        return reviewRepository.findByBookId(bookId, pageable).map(ReviewResponse::from);
    }

    public List<ReviewResponse> getReviewsWithMinRating(Long bookId, int minRating) {
        requireBook(bookId);
        return reviewRepository.findByBookIdAndRatingGreaterThanEqualOrderByCreatedAtDesc(bookId, minRating)
                .stream()
                .map(ReviewResponse::from)
                .toList();
    }

    public Double getAverageRating(Long bookId) {
        requireBook(bookId);
        Double average = reviewRepository.findAverageRatingByBookId(bookId);
        return average == null ? null : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public ReviewResponse getReview(Long id) {
        return ReviewResponse.from(findReview(id));
    }

    @Transactional
    public ReviewResponse addReview(Long bookId, ReviewRequest request) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));
        Review review = new Review();
        apply(review, request);
        book.addReview(review);
        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Transactional
    public ReviewResponse updateReview(Long id, ReviewRequest request) {
        Review review = findReview(id);
        apply(review, request);
        return ReviewResponse.from(review);
    }

    @Transactional
    public void deleteReview(Long id) {
        Review review = findReview(id);
        review.getBook().getReviews().remove(review); // orphanRemoval deletes the row
    }

    private void requireBook(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new ResourceNotFoundException("Book", bookId);
        }
    }

    private Review findReview(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review", id));
    }

    private void apply(Review review, ReviewRequest request) {
        review.setReviewerName(request.reviewerName().trim());
        review.setReviewerEmail(StringUtils.hasText(request.reviewerEmail()) ? request.reviewerEmail().trim() : null);
        review.setRating(request.rating());
        review.setComment(request.comment());
    }
}
