package com.template.springboottemplate.controller;

import com.template.springboottemplate.dto.CreateRequestDto;
import com.template.springboottemplate.model.ServiceRequest;
import com.template.springboottemplate.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

    @PostMapping("/{requestId}/payment")
    public ResponseEntity<String> makePayment(@PathVariable String requestId) {
        log.info("Received API request to initiate payment for request ID: {}", requestId);
        // Mock payment logic
        String paymentUrl = requestService.makePayment(requestId);
        return ResponseEntity.ok().body("{\"paymentUrl\": \"" + paymentUrl + "\"}");
    }
}
