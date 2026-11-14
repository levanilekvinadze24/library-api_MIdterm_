package ge.kiu.library.controller;

import ge.kiu.library.dto.PageResponse;
import ge.kiu.library.dto.ReviewRequest;
import ge.kiu.library.dto.ReviewResponse;
import ge.kiu.library.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Reviews", description = "Reviews of books (Book 1 -> * Review)")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/books/{bookId}/reviews")
    @Operation(summary = "Reviews of a book, paginated and sorted (newest first by default)")
    public PageResponse<ReviewResponse> getReviews(
            @PathVariable Long bookId,
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(reviewService.getReviewsOfBook(bookId, pageable), review -> review);
    }

    @GetMapping("/books/{bookId}/reviews/positive")
    @Operation(summary = "Reviews of a book with rating >= minRating (method-name query)")
    public List<ReviewResponse> getPositiveReviews(@PathVariable Long bookId,
                                                   @RequestParam(defaultValue = "4") @Min(1) @Max(5) int minRating) {
        return reviewService.getReviewsWithMinRating(bookId, minRating);
    }

    @GetMapping("/books/{bookId}/reviews/average")
    @Operation(summary = "Average rating of a book (custom JPQL @Query)")
    public Map<String, Object> getAverageRating(@PathVariable Long bookId) {
        Map<String, Object> body = new HashMap<>();
        body.put("bookId", bookId);
        body.put("averageRating", reviewService.getAverageRating(bookId)); // may be null -> HashMap
        return body;
    }

    @PostMapping("/books/{bookId}/reviews")
    @Operation(summary = "Add a review to a book")
    public ResponseEntity<ReviewResponse> addReview(@PathVariable Long bookId,
                                                    @Valid @RequestBody ReviewRequest request) {
        ReviewResponse created = reviewService.addReview(bookId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/reviews/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/reviews/{id}")
    @Operation(summary = "Get one review")
    public ReviewResponse getReview(@PathVariable Long id) {
        return reviewService.getReview(id);
    }

    @PutMapping("/reviews/{id}")
    @Operation(summary = "Update a review")
    public ReviewResponse updateReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return reviewService.updateReview(id, request);
    }

    @DeleteMapping("/reviews/{id}")
    @Operation(summary = "Delete a review")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}
