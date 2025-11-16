package com.neovation.controller;

import com.neovation.dto.CreateRequestDto;
import com.neovation.dto.UpdateRequestDto;
import com.neovation.model.ServiceRequest;
import com.neovation.service.RequestService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /**
     * Deletes a service request by its ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRequest(@PathVariable Long id) {
        log.info("Received API request to delete service request ID: {}", id);
        try {
            requestService.deleteRequest(id);
            // HTTP 204 No Content is standard for successful deletion
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            log.warn("Service request ID {} not found for deletion", id);
            return ResponseEntity.notFound().build(); // HTTP 404
        } catch (AccessDeniedException e) {
            log.warn("Access denied for deleting request ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); // HTTP 403
        } catch (Exception e) {
            log.error("An unexpected error occurred while deleting request ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred."); // HTTP 500
        }
    }

    /**
     * Gets a temporary signed URL for downloading a file attachment.
     */
    @GetMapping("/attachments/{id}/download-url")
    public ResponseEntity<?> getAttachmentDownloadUrl(@PathVariable Long id) {
        log.info("Received API request for download URL for attachment ID: {}", id);
        try {
            String downloadUrl = requestService.getAttachmentDownloadUrl(id);
            // Return the URL in a JSON object
            return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
        } catch (EntityNotFoundException e) {
            log.warn("Attachment or request not found for ID {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build(); // HTTP 404
        } catch (AccessDeniedException e) {
            log.warn("Access denied for downloading attachment ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); // HTTP 403
        } catch (Exception e) {
            log.error("An unexpected error occurred while generating download URL for attachment {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not generate download URL."); // HTTP 500
        }
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<?> deleteAttachment(@PathVariable Long id) {
        log.info("Received API request to delete attachment ID: {}", id);
        try {
            requestService.deleteAttachment(id);
            // HTTP 204 No Content is standard for successful deletion
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            log.warn("Attachment or request not found for ID {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build(); // HTTP 404
        } catch (AccessDeniedException e) {
            log.warn("Access denied for deleting attachment ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); // HTTP 403
        } catch (Exception e) {
            log.error("An unexpected error occurred while deleting attachment {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not delete file."); // HTTP 500
        }
    }
}
