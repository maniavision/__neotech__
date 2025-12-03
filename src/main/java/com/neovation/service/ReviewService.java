package com.neovation.service;

import com.neovation.dto.ReviewDto;
import com.neovation.model.Review;
import com.neovation.model.ServiceRequest;
import com.neovation.model.User;
import com.neovation.repository.ReviewRepository;
import com.neovation.repository.ServiceRequestRepository;
import com.neovation.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReviewService {
    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository, ServiceRequestRepository requestRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("User not found or not authenticated."));
    }

    private ServiceRequest findServiceRequest(String requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found with id: " + requestId));
    }

    private void checkUserOwnership(User currentUser, ServiceRequest request) {
        if (request.getUserId() == null || !request.getUserId().equals(currentUser.getId())) {
            log.warn("Access Denied: User {} attempted to review request {} not owned by them.", currentUser.getId(), request.getId());
            throw new AccessDeniedException("You do not have permission to review this request.");
        }
    }

    /**
     * Creates a new review for a service request. Only the request owner can review.
     */
    public ReviewDto createReview(String requestId, ReviewDto dto) {
        User currentUser = getCurrentUser();
        ServiceRequest request = findServiceRequest(requestId);
        checkUserOwnership(currentUser, request);

        // Check if a review already exists for this user/request pair
        Optional<Review> existingReview = reviewRepository.findByUserIdAndServiceRequestId(currentUser.getId(), requestId);
        if (existingReview.isPresent()) {
            throw new IllegalArgumentException("A review already exists for this request by the current user.");
        }

        Review newReview = new Review();
        newReview.setUser(currentUser);
        newReview.setServiceRequest(request);
        newReview.setRating(dto.getRating());
        newReview.setComment(dto.getComment());

        Review savedReview = reviewRepository.save(newReview);
        log.info("Created new review ID {} for request {}", savedReview.getId(), requestId);

        return mapToDto(savedReview);
    }

    /**
     * Gets all reviews for a specific request ID (staff/admin view)
     */
    public List<ReviewDto> getAllReviewsByRequestId(String requestId) {
        findServiceRequest(requestId); // Ensure request exists
        List<Review> reviews = reviewRepository.findByServiceRequestId(requestId);

        return reviews.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Gets the current user's review for a specific request ID
     */
    public ReviewDto getMyReviewByRequestId(String requestId) {
        User currentUser = getCurrentUser();
        ServiceRequest request = findServiceRequest(requestId);
        checkUserOwnership(currentUser, request); // Ensure user has access/ownership

        Review review = reviewRepository.findByUserIdAndServiceRequestId(currentUser.getId(), requestId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found for this request by the current user."));

        return mapToDto(review);
    }

    /**
     * Updates the current user's review for a service request.
     */
    public ReviewDto updateReview(String requestId, Long reviewId, ReviewDto dto) {
        User currentUser = getCurrentUser();
        findServiceRequest(requestId); // Ensure request exists

        Review existingReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found with id: " + reviewId));

        // Security Check: Review must belong to the current user AND the request
        if (!existingReview.getUser().getId().equals(currentUser.getId()) || !existingReview.getServiceRequest().getId().equals(requestId)) {
            throw new AccessDeniedException("You do not have permission to modify this review.");
        }

        existingReview.setRating(dto.getRating());
        existingReview.setComment(dto.getComment());

        Review updatedReview = reviewRepository.save(existingReview);
        log.info("Updated review ID {} for request {}", reviewId, requestId);

        return mapToDto(updatedReview);
    }

    /**
     * Deletes the current user's review for a service request.
     */
    public void deleteReview(String requestId, Long reviewId) {
        User currentUser = getCurrentUser();
        findServiceRequest(requestId); // Ensure request exists

        Review existingReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found with id: " + reviewId));

        // Security Check: Review must belong to the current user AND the request
        if (!existingReview.getUser().getId().equals(currentUser.getId()) || !existingReview.getServiceRequest().getId().equals(requestId)) {
            throw new AccessDeniedException("You do not have permission to delete this review.");
        }

        reviewRepository.delete(existingReview);
        log.info("Deleted review ID {} for request {}", reviewId, requestId);
    }

    private ReviewDto mapToDto(Review review) {
        ReviewDto dto = new ReviewDto();
        dto.setId(review.getId());
        dto.setServiceRequestId(review.getServiceRequest().getId());
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setUserId(review.getUser().getId());
        dto.setUserName(review.getUser().getFirstName() + " " + review.getUser().getLastName());
        return dto;
    }
}