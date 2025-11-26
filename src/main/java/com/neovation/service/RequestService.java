package com.neovation.service;

import com.neovation.dto.*;
import com.neovation.model.*;
import com.neovation.repository.FileAttachmentRepository;
import com.neovation.repository.PaymentRepository;
import com.neovation.repository.ServiceRequestRepository;
import com.neovation.repository.UserRepository;
import com.stripe.exception.StripeException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RequestService {
    private static final Logger log = LoggerFactory.getLogger(RequestService.class);
    final private ServiceRequestRepository serviceRequestRepository;
    final private UserRepository userRepository;
    final private UserService userService;
    final private FileStorageService fileStorageService;
    final private FileAttachmentRepository fileAttachmentRepository;
    final private StripePaymentService stripePaymentService; // <--- NEW FIELD
    final private PaymentRepository paymentRepository;

    public RequestService(ServiceRequestRepository serviceRequestRepository, UserRepository userRepository, UserService userService, FileStorageService fileStorageService, FileAttachmentRepository fileAttachmentRepository, StripePaymentService stripePaymentService, PaymentRepository paymentRepository) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.fileAttachmentRepository = fileAttachmentRepository;
        this.stripePaymentService = stripePaymentService;
        this.paymentRepository = paymentRepository;
    }

    public ServiceRequest createRequest(CreateRequestDto requestData) {
        log.info("Processing new service request for email: {}", requestData.getEmail());
        User user = getCurrentUser(requestData.getEmail());

        if (user == null && requestData.getEmail() != null) {
            log.info("No existing user found. Registering new user for: {}", requestData.getEmail());
            NewUserDto newUserDto = new NewUserDto(
                    requestData.getFirstName(),
                    requestData.getLastName(),
                    requestData.getCompanyName(),
                    requestData.getEmail(),
                    requestData.getPhone(),
                    UUID.randomUUID().toString(),
                    requestData.getCountryCode()
            );
            user = userService.register(newUserDto);
            log.info("New user registered with ID: {}", user.getId());
        }

        ServiceRequest serviceRequest = new ServiceRequest();
        if (user != null) {
            serviceRequest.setUserId(user.getId());
        }
        serviceRequest.setTitle(requestData.getTitle());
        serviceRequest.setService(requestData.getService());
        serviceRequest.setDescription(requestData.getDescription());
        serviceRequest.setBudgetRange(requestData.getBudgetRange());
        serviceRequest.setExpectedDueDate(requestData.getExpectedDueDate());
        serviceRequest.setStatus(RequestStatus.SUBMITTED);
        serviceRequest.setCreatedAt(LocalDateTime.now());
        serviceRequest.setUpdatedAt(LocalDateTime.now());

        if (requestData.getAttachments() != null && !requestData.getAttachments().isEmpty()) {
            log.info("Processing {} attachments for new request", requestData.getAttachments().size());
            List<FileAttachment> attachments = new ArrayList<>();
            for (MultipartFile file : requestData.getAttachments()) {
                assert user != null;
                String gcsPath = fileStorageService.storeFile(file, user.getId());

                FileAttachment attachment = new FileAttachment();
                // Store the original file name for display
                attachment.setFileName(file.getOriginalFilename());
                attachment.setFileSize(file.getSize());
                attachment.setFileType(file.getContentType());
                // Store the GCS path in the URL field
                attachment.setUrl(gcsPath);
                attachments.add(attachment);
            }
            serviceRequest.setAttachments(attachments);
            log.info("Attached {} files to service request", attachments.size());
        }
        ServiceRequest savedRequest = serviceRequestRepository.save(serviceRequest);
        log.info("Successfully created and saved new service request with ID: {}", savedRequest.getId());

        // Send a confirmation email to the user <--- ADDED LOGIC
        if (user != null) {
            userService.sendRequestCreatedEmail(savedRequest);
        }

        // Send internal alert email to the company/admin <--- ADDED LOGIC
        userService.sendNewRequestAlertEmail(savedRequest);
        return savedRequest;
    }

    public List<ServiceRequest> getUserRequests(RequestStatus status, String sortBy, String sortDir) {
        User user = getCurrentUser(null);
        if (user != null) {
            log.info("Fetching requests for user ID: {}", user.getId());

            // Determine sort direction
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

            // Map "dueDate" to the actual entity property "expectedDueDate" if necessary,
            // otherwise default to "createdAt" or use the provided field.
            String sortProperty = "createdAt";
            if (sortBy != null && !sortBy.isEmpty()) {
                if ("dueDate".equals(sortBy)) {
                    sortProperty = "expectedDueDate";
                } else {
                    sortProperty = sortBy;
                }
            }

            Sort sort = Sort.by(direction, sortProperty);

            if (status != null) {
                return serviceRequestRepository.findByUserIdAndStatus(user.getId(), status, sort);
            } else {
                return serviceRequestRepository.findByUserId(user.getId(), sort);
            }
        }
        log.warn("Could not find authenticated user to fetch requests.");
        return new ArrayList<>();
    }

    public ServiceRequestDto getRequestById(Long id) {
        log.info("Fetching request by ID: {}", id);

        ServiceRequest request = serviceRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found with id: " + id));

        User currentUser = getCurrentUser(null);

        // Security Check: Must be ADMIN/STAFF/MANAGER or the owner
        if (currentUser == null || (
                !currentUser.getRole().equals(Role.ADMIN) &&
                        !currentUser.getRole().equals(Role.STAFF) &&
                        !currentUser.getRole().equals(Role.MANAGER) &&
                        !request.getUserId().equals(currentUser.getId()))) {
            log.warn("Access denied: User {} attempting to view request {} owned by user {}",
                    currentUser != null ? currentUser.getId() : "null", request.getId(), request.getUserId());
            throw new AccessDeniedException("Access denied to view this resource.");
        }

        return mapToDto(request);
    }

    /**
     * Replaced mock payment logic with Stripe integration.
     * @param requestId The ID of the Service Request
     * @param paymentDto The DTO containing the requested amount and email
     */
    public String makePayment(Long requestId, PaymentRequestDto paymentDto) { // <--- MODIFIED SIGNATURE
        log.info("Generating Stripe payment link for request ID: {} with requested amount: {}", requestId, paymentDto.getAmount());

        ServiceRequest request = serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found with id: " + requestId));

        BigDecimal requestedAmount = paymentDto.getAmount();
        BigDecimal requiredPrice = request.getPrice();

        if (requiredPrice == null || requiredPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Payment failed: Required price is missing or zero for request ID: {}", requestId);
            throw new IllegalArgumentException("Cannot create payment session. Service price is not set.");
        }

        // 1. Determine Payment Status based on requested amount vs. required price
        PaymentStatus initialStatus;
        if (requestedAmount.compareTo(requiredPrice) < 0) {
            initialStatus = PaymentStatus.PARTIAL; // requested amount < price
        } else {
            // requested amount >= price. Setting to COMPLETED, acknowledging this may be temporary until Stripe webhook confirms success.
            initialStatus = PaymentStatus.COMPLETED;
        }

        // 2. Create a new Payment record
        Payment payment = new Payment();
        payment.setServiceRequest(request);
        payment.setAmount(requestedAmount); // Store the amount the customer intends to pay
        payment.setEmail(paymentDto.getEmail()); // <--- NEW FIELD SET
        payment.setPaymentStatus(initialStatus); // <--- STATUS SET BASED ON BUSINESS LOGIC
        payment.setPaymentProvider("Stripe");

        Payment savedPayment = paymentRepository.save(payment);

        log.info("Created payment record ID {} for request ID {} with status {}", savedPayment.getId(), requestId, initialStatus);

        try {
            // 3. Create the Stripe Checkout Session
            return stripePaymentService.createCheckoutSession(request.getId(), savedPayment.getId());
        } catch (StripeException e) {
            log.error("Stripe API error while creating checkout session for request ID {}: {}", requestId, e.getMessage());
            // Delete the local payment record if Stripe session creation fails.
            paymentRepository.delete(savedPayment);
            throw new RuntimeException("Payment processing failed due to an external error.", e);
        }
    }

    private User getCurrentUser(String email) {
        if (email != null) {
            return userRepository.findByEmail(email).orElse(null);
        }
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(userEmail).orElse(null);
    }

    public ServiceRequest updateRequest(Long id, UpdateRequestDto updateData) {
        log.info("Attempting to update service request ID: {}", id);

        // Find the existing request or throw an exception
        ServiceRequest existingRequest = serviceRequestRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update failed: Service request not found with ID: {}", id);
                    return new EntityNotFoundException("ServiceRequest not found with id: " + id);
                });

        // --- Update fields only if they are provided in the DTO ---
        if (updateData.getTitle() != null) {
            existingRequest.setTitle(updateData.getTitle());
        }
        if (updateData.getPrice() != null) {
            // You could add validation here, e.g., ensure price is positive
            existingRequest.setPrice(updateData.getPrice());
        }
        if (updateData.getStatus() != null) {
            existingRequest.setStatus(updateData.getStatus());
        }
        if (updateData.getService() != null) {
            existingRequest.setService(updateData.getService());
        }
        if (updateData.getDescription() != null) {
            existingRequest.setDescription(updateData.getDescription());
        }
        if (updateData.getBudgetRange() != null) {
            existingRequest.setBudgetRange(updateData.getBudgetRange());
        }
        if (updateData.getExpectedDueDate() != null) {
            existingRequest.setExpectedDueDate(updateData.getExpectedDueDate());
        }

        // Note: You may need to add logic here to update countryCode if it's tied to the user, not the request.
        // If it's on the request, you'd find and set the Country entity.

        // Handle new file attachments
        if (updateData.getAttachments() != null && !updateData.getAttachments().isEmpty()) {
            log.info("Processing {} new attachments for request ID: {}", updateData.getAttachments().size(), id);

            // Initialize attachments list if it's null
            if (existingRequest.getAttachments() == null) {
                existingRequest.setAttachments(new ArrayList<>());
            }

            for (MultipartFile file : updateData.getAttachments()) {
                String gcsPath = fileStorageService.storeFile(file, existingRequest.getUserId());
                FileAttachment attachment = new FileAttachment();
                attachment.setFileName(file.getOriginalFilename());
                attachment.setFileSize(file.getSize());
                attachment.setFileType(file.getContentType());
                attachment.setUrl(gcsPath); // Adjust URL as needed
                existingRequest.getAttachments().add(attachment);
            }
            log.info("Added {} new files to service request {}", updateData.getAttachments().size(), id);
        }

        // Save and return the updated request
        ServiceRequest updatedRequest = serviceRequestRepository.save(existingRequest);
        log.info("Successfully updated service request ID: {}", id);
        return updatedRequest;
    }

    /**
     * Deletes a service request and all associated files from GCS.
     * Only the user who created the request or an ADMIN can perform this action.
     *
     * @param id The ID of the service request to delete.
     */
    public void deleteRequest(Long id) {
        log.info("Attempting to delete service request ID: {}", id);

        // 1. Get the authenticated user
        User currentUser = getCurrentUser(null);
        if (currentUser == null) {
            log.warn("Delete failed: No authenticated user.");
            throw new AccessDeniedException("User not authenticated.");
        }

        // 2. Find the request
        ServiceRequest request = serviceRequestRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delete failed: Service request not found with ID: {}", id);
                    return new EntityNotFoundException("ServiceRequest not found with id: " + id);
                });

        // 3. Security Check: User must be ADMIN or the owner of the request
        if (!currentUser.getRole().equals(Role.ADMIN) && !request.getUserId().equals(currentUser.getId())) {
            log.warn("Access denied: User {} attempting to delete request {} owned by user {}",
                    currentUser.getId(), request.getId(), request.getUserId());
            throw new AccessDeniedException("Access denied to delete this resource.");
        }

        // 4. Get the list of file paths *before* deleting the DB record
        List<String> pathsToDelete = new ArrayList<>();
        if (request.getAttachments() != null) {
            for (FileAttachment attachment : request.getAttachments()) {
                if (attachment.getUrl() != null && !attachment.getUrl().isEmpty()) {
                    pathsToDelete.add(attachment.getUrl());
                }
            }
        }
        log.debug("Found {} file attachments to delete from GCS for request ID: {}", pathsToDelete.size(), id);

        // 5. Delete the request from the database.
        // Because @OneToMany on attachments is CascadeType.ALL,
        // this will also delete the FileAttachment entries in the database.
        serviceRequestRepository.delete(request);
        log.info("Successfully deleted service request record ID: {}", id);

        // 6. Delete associated files from GCS
        for (String path : pathsToDelete) {
            fileStorageService.deleteFile(path);
        }
        log.info("Completed GCS file cleanup for request ID: {}", id);
    }

    /**
     * Generates a signed download URL for a specific attachment.
     *
     * @param attachmentId The ID of the FileAttachment.
     * @return A temporary, signed URL for downloading the file.
     */
    public String getAttachmentDownloadUrl(Long attachmentId) {
        log.info("Generating download URL for attachment ID: {}", attachmentId);

        // 1. Get the authenticated user
        User currentUser = getCurrentUser(null);
        if (currentUser == null) {
            throw new AccessDeniedException("User not authenticated.");
        }

        // 2. Find the attachment itself to get the blob path
        FileAttachment attachment = fileAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("FileAttachment not found with id: " + attachmentId));

        // 3. Find the parent request using the attachment ID for a security check
        ServiceRequest request = serviceRequestRepository.findByAttachments_Id(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found for attachment id: " + attachmentId));

        // 4. Security Check: User must be ADMIN or the owner of the request
        if (!currentUser.getRole().equals(Role.ADMIN) && !request.getUserId().equals(currentUser.getId())) {
            log.warn("Access denied: User {} attempting to download attachment {} from request {}",
                    currentUser.getId(), attachmentId, request.getId());
            throw new AccessDeniedException("Access denied to download this file.");
        }

        // 5. Get blob path (which is stored in the 'url' field)
        String blobPath = attachment.getUrl();
        if (blobPath == null || blobPath.isEmpty()) {
            log.error("Attachment ID {} has a null or empty file path.", attachmentId);
            throw new RuntimeException("File path is missing for this attachment.");
        }

        // 6. Generate the signed URL
        return fileStorageService.generateSignedDownloadUrl(blobPath);
    }

    /**
     * Deletes a single file attachment from the database and GCS.
     *
     * @param attachmentId The ID of the FileAttachment to delete.
     */
    public void deleteAttachment(Long attachmentId) {
        log.info("Attempting to delete attachment ID: {}", attachmentId);

        // 1. Get the authenticated user
        User currentUser = getCurrentUser(null);
        if (currentUser == null) {
            log.warn("Delete attachment failed: No authenticated user.");
            throw new AccessDeniedException("User not authenticated.");
        }

        // 2. Find the attachment by its own ID
        FileAttachment attachment = fileAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> {
                    log.warn("Delete failed: FileAttachment not found with ID: {}", attachmentId);
                    return new EntityNotFoundException("FileAttachment not found with id: " + attachmentId);
                });

        // 3. Find the parent request for security checks
        ServiceRequest request = serviceRequestRepository.findByAttachments_Id(attachmentId)
                .orElseThrow(() -> {
                    // This case should rarely happen, but it protects against orphaned files
                    log.error("Data integrity issue: No parent ServiceRequest found for attachment ID: {}", attachmentId);
                    return new EntityNotFoundException("Parent request not found for attachment.");
                });

        // 4. Security Check: User must be ADMIN or the owner of the request
        if (!currentUser.getRole().equals(Role.ADMIN) && !request.getUserId().equals(currentUser.getId())) {
            log.warn("Access denied: User {} attempting to delete attachment {} from request {}",
                    currentUser.getId(), attachmentId, request.getId());
            throw new AccessDeniedException("Access denied to delete this file.");
        }

        // 5. Get the GCS blob path *before* deleting the DB record
        String blobPath = attachment.getUrl();

        // 6. Delete the attachment from the database
        // This removes the row from the file_attachments table.
        fileAttachmentRepository.delete(attachment);
        log.info("Successfully deleted attachment record ID: {}", attachmentId);

        // 7. Delete the file from GCS
        if (blobPath != null && !blobPath.isEmpty()) {
            fileStorageService.deleteFile(blobPath);
            log.info("Triggered GCS file deletion for path: {}", blobPath);
        } else {
            log.warn("Attachment record {} had no GCS path; nothing to delete from storage.", attachmentId);
        }
    }

    /**
     * Allows an admin, staff, or manager to add an attachment to any service request.
     *
     * @param requestId The ID of the request to update.
     * @param file      The file to attach.
     * @return The updated ServiceRequest.
     */
    public ServiceRequestDto addAttachmentToRequest(Long requestId, MultipartFile file, String purposeStr) {
        log.info("Attempting to add attachment to request ID: {} with purpose: {}", requestId, purposeStr);

        // 1. Security Check: Verify the current user has the required role
        User currentUser = getCurrentUser(null);
        if (currentUser == null) {
            log.warn("Attachment upload failed: No authenticated user.");
            throw new AccessDeniedException("User not authenticated.");
        }

        FilePurpose purpose;
        try {
            purpose = FilePurpose.valueOf(purposeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid file purpose provided: {}", purposeStr);
            throw new IllegalArgumentException("Invalid file purpose: " + purposeStr + ". Must be one of: " + FilePurpose.values());
        }

        Role role = currentUser.getRole();
        if (role != Role.ADMIN && role != Role.STAFF && role != Role.MANAGER && role != Role.USER) {
            log.warn("Access Denied: User {} with role {} tried to add attachment to request {}",
                    currentUser.getEmail(), role, requestId);
            throw new AccessDeniedException("You do not have permission to perform this action.");
        }

        // 2. Find the request
        ServiceRequest existingRequest = serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.warn("Attachment upload failed: Service request not found with ID: {}", requestId);
                    return new EntityNotFoundException("ServiceRequest not found with id: " + requestId);
                });

        // 3. Upload the file to GCS
        // We use the request owner's (user.getId()) folder for consistency
        String gcsPath = fileStorageService.storeFile(file, existingRequest.getUserId());

        // 4. Create the FileAttachment entity
        FileAttachment attachment = new FileAttachment();
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());
        attachment.setUrl(gcsPath);
        attachment.setPurpose(purpose);

        // 5. Add to the request and save
        if (existingRequest.getAttachments() == null) {
            existingRequest.setAttachments(new ArrayList<>());
        }
        existingRequest.getAttachments().add(attachment);
        ServiceRequest updatedRequest = serviceRequestRepository.save(existingRequest);

        log.info("Successfully added new attachment by user {} to request ID: {}", currentUser.getEmail(), requestId);

        // 6. Check if a proposal was uploaded and send email <--- ADDED LOGIC
        if (purpose == FilePurpose.PROPOSAL) {
            userService.sendProposalUploadedEmail(updatedRequest);
        }

        return mapToDto(updatedRequest);
    }

    /**
     * Retrieves all service requests for a specific user ID, with optional filtering and sorting.
     */
    public List<ServiceRequestDto> getAllRequestsByUserId(Long userId, RequestStatus status, String sortBy, String sortDir) {
        log.info("Admin/Staff/Manager fetching requests for user ID: {}", userId);

        // No security check here; handled in the controller (SecurityConfig)
        List<ServiceRequest> requests = new ArrayList<>();
        // Determine sort direction
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        // Map "dueDate" to the actual entity property "expectedDueDate"
        String sortProperty = "createdAt";
        if (sortBy != null && !sortBy.isEmpty()) {
            if ("dueDate".equals(sortBy)) {
                sortProperty = "expectedDueDate";
            } else {
                sortProperty = sortBy;
            }
        }

        Sort sort = Sort.by(direction, sortProperty);

        if (status != null) {
            requests = serviceRequestRepository.findByUserIdAndStatus(userId, status, sort);
        } else {
            requests = serviceRequestRepository.findByUserId(userId, sort);
        }
        return requests.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private ServiceRequestDto mapToDto(ServiceRequest request) {
        ServiceRequestDto dto = new ServiceRequestDto();
        dto.setId(request.getId());
        dto.setUserId(request.getUserId());
        dto.setTitle(request.getTitle());
        dto.setService(request.getService());
        dto.setDescription(request.getDescription());
        dto.setBudgetRange(request.getBudgetRange());
        dto.setPrice(request.getPrice());
        dto.setStatus(request.getStatus());
        dto.setExpectedDueDate(request.getExpectedDueDate());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setUpdatedAt(request.getUpdatedAt());

        // Set attachment count (handles lazy loading check)
        if (request.getAttachments() != null) {
            dto.setAttachments(
                    request.getAttachments().stream()
                            .map(this::mapToFileAttachmentDto) // <--- MODIFIED
                            .collect(Collectors.toList())
            );
        } else {
            dto.setAttachments(new ArrayList<>());
        }
        return dto;
    }

    private FileAttachmentDto mapToFileAttachmentDto(FileAttachment attachment) {
        FileAttachmentDto dto = new FileAttachmentDto();
        dto.setId(attachment.getId());
        dto.setFileName(attachment.getFileName());
        dto.setFileSize(attachment.getFileSize());
        dto.setFileType(attachment.getFileType());
        dto.setUrl(attachment.getUrl());
        dto.setPurpose(attachment.getPurpose());
        return dto;
    }
}
