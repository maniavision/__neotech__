package com.neovation.repository;

import com.neovation.model.RequestStatus;
import com.neovation.model.ServiceRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByUserId(Long userId, Sort sort);
    // Add method for filtering by Status with Sort
    List<ServiceRequest> findByUserIdAndStatus(Long userId, RequestStatus status, Sort sort);
    Optional<ServiceRequest> findByAttachments_Id(Long attachmentId);
}