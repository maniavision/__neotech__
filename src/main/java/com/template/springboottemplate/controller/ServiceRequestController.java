package com.template.springboottemplate.controller;

import com.template.springboottemplate.dto.CreateRequestDto;
import com.template.springboottemplate.service.RequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class ServiceRequestController {

    private final RequestService requestService;

    public ServiceRequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CreateRequestDto> createRequest(@ModelAttribute CreateRequestDto createRequestDto, @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : null;
        CreateRequestDto newRequest = requestService.createRequest(createRequestDto);
        return ResponseEntity.ok(newRequest);
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<CreateRequestDto>> getUserRequests(@AuthenticationPrincipal UserDetails userDetails) {
        List<CreateRequestDto> requests = requestService.getUserRequests(userDetails.getUsername());
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreateRequestDto> getRequestById(@PathVariable String id, @AuthenticationPrincipal UserDetails userDetails) {
        CreateRequestDto request = requestService.getRequestById(id, userDetails.getUsername());
        return ResponseEntity.ok(request);
    }

    @PostMapping("/{requestId}/payment")
    public ResponseEntity<String> makePayment(@PathVariable String requestId) {
        // Mock payment logic
        String paymentUrl = "https://example.com/payment/" + requestId;
        return ResponseEntity.ok().body("{\"paymentUrl\": \"" + paymentUrl + "\"}");
    }
}
