package com.neovation.repository;

import com.neovation.model.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByUserId(Long userId);
    Optional<ServiceRequest> findByAttachments_Id(Long attachmentId);
}