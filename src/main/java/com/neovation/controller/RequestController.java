package com.neovation.controller;

import com.neovation.dto.*;
import com.neovation.model.RequestStatus;
import com.neovation.model.ServiceRequest;
import com.neovation.service.RequestService;
import com.neovation.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin
@RestController
@RequestMapping("/api/requests")
public class RequestController {
    private static final Logger log = LoggerFactory.getLogger(RequestController.class);
    private final RequestService requestService;
    private final UserService userService;

    public RequestController(RequestService requestService, UserService userService) {
        this.requestService = requestService;
        this.userService = userService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ServiceRequest> createRequest(@ModelAttribute CreateRequestDto createRequestDto) {
        log.info("Received API request to create service request from: {}", createRequestDto.getEmail());
        ServiceRequest newRequest = requestService.createRequest(createRequestDto);
        return ResponseEntity.ok(newRequest);
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<ServiceRequest>> getUserRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {

        log.info("Received API request to fetch user's requests. Status: {}, SortBy: {}, Dir: {}", status, sortBy, sortDir);

        // Pass parameters to the service
        List<ServiceRequest> requests = requestService.getUserRequests(status, sortBy, sortDir);

        return ResponseEntity.ok(requests);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceRequestDto> getRequestById(@PathVariable String id) {
        log.info("Received API request to fetch service request ID: {}", id);
        ServiceRequestDto request = requestService.getRequestById(id);
        if (request == null) {
            log.warn("Service request ID {} not found", id);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(request);
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<ServiceRequestDto> updateRequest(@PathVariable String id, @ModelAttribute UpdateRequestDto updateRequestDto) {
        log.info("Received API request to update service request ID: {}", id);
        try {
            ServiceRequestDto updatedRequest = requestService.updateRequest(id, updateRequestDto);
            return ResponseEntity.ok(updatedRequest);
        } catch (EntityNotFoundException e) {
            log.warn("Service request ID {} not found for update", id);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{requestId}/payment") // <--- Uses Path Variable for requestId
    public ResponseEntity<?> makePayment(@PathVariable String requestId, @RequestBody @Valid PaymentRequestDto paymentDto) { // <--- MODIFIED SIGNATURE
        log.info("Received API request to initiate payment for request ID: {} with amount: {}", requestId, paymentDto.getAmount());
        try {
            // Pass the requestId from path variable and the DTO to the service
            String paymentUrl = requestService.makePayment(requestId, paymentDto);
            // Return the URL in a custom DTO
            return ResponseEntity.ok(new StripeCheckoutResponse(paymentUrl));
        } catch (EntityNotFoundException e) {
            log.warn("Payment initiation failed: Request ID {} not found.", requestId);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Payment initiation failed for request ID {}: {}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RuntimeException e) {
            log.error("An unexpected error occurred during payment for request ID {}: {}", requestId, e.getMessage(), e);
            // This handles StripeException wrapped by the service
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        }
    }

    /**
     * Deletes a service request by its ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRequest(@PathVariable String id) {
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

    /**
     * Endpoint for Admin/Staff/Manager to upload an attachment to any request.
     */
    @PostMapping(value = "/{id}/attachments", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadFileToRequest(@PathVariable String id, @RequestParam("file") MultipartFile file,
                                                 @RequestParam(name = "purpose", required = false, defaultValue = "USER_FILE") String purpose,
                                                 @RequestParam(name = "lang", required = false) String lang) {
        log.info("Received API request to add attachment to request ID: {} with purpose: {}", id, purpose);
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File cannot be empty.");
        }
        try {
            ServiceRequestDto updatedRequest = requestService.addAttachmentToRequest(id, file, purpose, lang);
            // Return the updated request, or perhaps just the new attachment
            // Returning the request is consistent with the updateRequest endpoint
            return ResponseEntity.ok(updatedRequest);
        } catch (EntityNotFoundException e) {
            log.warn("Attachment upload failed: Request ID {} not found", id);
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            log.warn("Access denied for uploading attachment to request ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred while adding attachment to request ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    /**
     * ADMIN/STAFF/MANAGER endpoint to list all service requests for a specific user ID.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ServiceRequestDto>> getRequestsByUserId(
            @PathVariable Long userId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {

        log.info("Received API request to fetch requests for user ID: {}", userId);

        // Optional: Check if the user ID exists before proceeding
        if (userService.findUserById(userId).isEmpty()) {
            log.warn("Request to fetch user requests failed: User ID {} not found.", userId);
            return ResponseEntity.notFound().build(); // HTTP 404
        }

        // Fetch requests using the new service method
        List<ServiceRequestDto> requests = requestService.getAllRequestsByUserId(userId, status, sortBy, sortDir);

        return ResponseEntity.ok(requests);
    }

}
