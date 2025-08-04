package com.template.springboottemplate.controller;

import com.template.springboottemplate.dto.CreateRequestDto;
import com.template.springboottemplate.model.ServiceRequest;
import com.template.springboottemplate.service.RequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ServiceRequest> createRequest(@ModelAttribute CreateRequestDto createRequestDto) {
        ServiceRequest newRequest = requestService.createRequest(createRequestDto);
        return ResponseEntity.ok(newRequest);
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<ServiceRequest>> getUserRequests() {
        List<ServiceRequest> requests = requestService.getUserRequests();
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceRequest> getRequestById(@PathVariable String id) {
        ServiceRequest request = requestService.getRequestById(id);
        return ResponseEntity.ok(request);
    }

    @PostMapping("/{requestId}/payment")
    public ResponseEntity<String> makePayment(@PathVariable String requestId) {
        // Mock payment logic
        String paymentUrl = requestService.makePayment(requestId);
        return ResponseEntity.ok().body("{\"paymentUrl\": \"" + paymentUrl + "\"}");
    }
}
