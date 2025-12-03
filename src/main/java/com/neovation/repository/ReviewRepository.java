package com.neovation.repository;

import com.neovation.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    /**
     * Find all reviews associated with a specific service request.
     */
    List<Review> findByServiceRequestId(String serviceRequestId);

    /**
     * Find a specific user's review for a specific service request.
     */
    Optional<Review> findByUserIdAndServiceRequestId(Long userId, String serviceRequestId);
}