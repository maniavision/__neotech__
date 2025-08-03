package com.template.springboottemplate.repository;

import com.template.springboottemplate.model.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, String> {
    List<ServiceRequest> findByUserId(Long userId);
}