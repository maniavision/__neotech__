package com.neovation.controller;

import com.neovation.dto.CreateRequestDto;
import com.neovation.dto.UpdateRequestDto;
import com.neovation.model.ServiceRequest;
import com.neovation.service.RequestService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/requests")
public class RequestController {
    private static final Logger log = LoggerFactory.getLogger(RequestController.class);
    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ServiceRequest> createRequest(@ModelAttribute CreateRequestDto createRequestDto) {
        log.info("Received API request to create service request from: {}", createRequestDto.getEmail());
        ServiceRequest newRequest = requestService.createRequest(createRequestDto);
        return ResponseEntity.ok(newRequest);
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<ServiceRequest>> getUserRequests() {
        log.info("Received API request to fetch user's requests");
        List<ServiceRequest> requests = requestService.getUserRequests();
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceRequest> getRequestById(@PathVariable Long id) {
        log.info("Received API request to fetch service request ID: {}", id);
        ServiceRequest request = requestService.getRequestById(id);
        if (request == null) {
            log.warn("Service request ID {} not found", id);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(request);
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<ServiceRequest> updateRequest(@PathVariable Long id, @ModelAttribute UpdateRequestDto updateRequestDto) {
        log.info("Received API request to update service request ID: {}", id);
        try {
            ServiceRequest updatedRequest = requestService.updateRequest(id, updateRequestDto);
            return ResponseEntity.ok(updatedRequest);
        } catch (EntityNotFoundException e) {
            log.warn("Service request ID {} not found for update", id);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{requestId}/payment")
    public ResponseEntity<String> makePayment(@PathVariable String requestId) {
        log.info("Received API request to initiate payment for request ID: {}", requestId);
        // Mock payment logic
        String paymentUrl = requestService.makePayment(requestId);
        return ResponseEntity.ok().body("{\"paymentUrl\": \"" + paymentUrl + "\"}");
    }
}
