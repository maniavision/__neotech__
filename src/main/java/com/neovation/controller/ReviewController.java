package com.neovation.controller;

import com.neovation.dto.ReviewDto;
import com.neovation.service.ReviewService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/reviews") // <--- CHANGED BASE MAPPING
public class ReviewController {
    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * POST /api/reviews - Creates a new review for the request.
     * The requestId is read from the DTO.
     */
    @PostMapping
    public ResponseEntity<ReviewDto> createReview(
            @RequestBody @Valid ReviewDto dto) { // <--- REMOVED @PathVariable

        String requestId = dto.getServiceRequestId(); // <--- READ FROM DTO
        log.info("Received POST to create review for request ID: {}", requestId);
        try {
            ReviewDto newReview = reviewService.createReview(requestId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(newReview);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build(); // 404
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
        } catch (IllegalArgumentException e) {
            // Catches "A review already exists"
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null); // 409
        }
    }

    /**
     * GET /api/reviews?requestId={requestId} - Gets all reviews for a request.
     */
    @GetMapping
    public ResponseEntity<List<ReviewDto>> getAllReviews(@RequestParam String requestId) { // <--- CHANGED TO @RequestParam
        log.info("Received GET to fetch all reviews for request ID: {}", requestId);
        try {
            List<ReviewDto> reviews = reviewService.getAllReviewsByRequestId(requestId);
            return ResponseEntity.ok(reviews);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build(); // 404
        }
    }

    /**
     * GET /api/reviews/me?requestId={requestId} - Gets the current user's review for a request.
     */
    @GetMapping("/me")
    public ResponseEntity<ReviewDto> getMyReview(@RequestParam String requestId) { // <--- CHANGED TO @RequestParam
        log.info("Received GET to fetch current user's review for request ID: {}", requestId);
        try {
            ReviewDto review = reviewService.getMyReviewByRequestId(requestId);
            return ResponseEntity.ok(review);
        } catch (EntityNotFoundException e) {
            // Returns 404 if the request ID is invalid OR if the user hasn't left a review yet.
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
        }
    }

    /**
     * PUT /api/reviews/{id}?requestId={requestId} - Updates an existing review.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReviewDto> updateReview(
            @RequestParam String requestId, // <--- CHANGED TO @RequestParam
            @PathVariable Long id,
            @RequestBody @Valid ReviewDto dto) {

        log.info("Received PUT to update review ID: {} for request ID: {}", id, requestId);
        try {
            ReviewDto updatedReview = reviewService.updateReview(requestId, id, dto);
            return ResponseEntity.ok(updatedReview);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build(); // 404
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
        }
    }

    /**
     * DELETE /api/reviews/{id}?requestId={requestId} - Deletes a review.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(
            @RequestParam String requestId, // <--- CHANGED TO @RequestParam
            @PathVariable Long id) {

        log.info("Received DELETE to delete review ID: {} for request ID: {}", id, requestId);
        try {
            reviewService.deleteReview(requestId, id);
            return ResponseEntity.noContent().build(); // 204
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build(); // 404
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
        }
    }
}