package com.template.springboottemplate.service;

import com.template.springboottemplate.dto.CreateRequestDto;
import com.template.springboottemplate.dto.NewUserDto;
import com.template.springboottemplate.model.FileAttachment;
import com.template.springboottemplate.model.RequestStatus;
import com.template.springboottemplate.model.ServiceRequest;
import com.template.springboottemplate.model.User;
import com.template.springboottemplate.repository.ServiceRequestRepository;
import com.template.springboottemplate.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);
    final private ServiceRequestRepository serviceRequestRepository;

    final private UserRepository userRepository;

    final private UserService userService;

    final private FileStorageService fileStorageService;

    public RequestService(ServiceRequestRepository serviceRequestRepository, UserRepository userRepository, UserService userService, FileStorageService fileStorageService) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
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
                    UUID.randomUUID().toString()
            );
            user = userService.register(newUserDto);
            log.info("New user registered with ID: {}", user.getId());
        }

        ServiceRequest serviceRequest = new ServiceRequest();
        if (user != null) {
            serviceRequest.setUserId(user.getId());
            serviceRequest.setUserName(user.getFirstName() + " " + user.getLastName());
            serviceRequest.setUserEmail(user.getEmail());
        } else {
            serviceRequest.setUserName(requestData.getFirstName() + " " + requestData.getLastName());
            serviceRequest.setUserEmail(requestData.getEmail());
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
                String fileName = fileStorageService.storeFile(file);
                FileAttachment attachment = new FileAttachment();
                attachment.setFileName(fileName);
                attachment.setFileSize(file.getSize());
                attachment.setFileType(file.getContentType());
                attachment.setUrl("/uploads/" + fileName); // Adjust URL as needed
                attachments.add(attachment);
            }
            serviceRequest.setAttachments(attachments);
            log.info("Attached {} files to service request", attachments.size());
        }
        ServiceRequest savedRequest = serviceRequestRepository.save(serviceRequest);
        log.info("Successfully created and saved new service request with ID: {}", savedRequest.getId());
        return savedRequest;
    }

    public List<ServiceRequest> getUserRequests() {
        User user = getCurrentUser(null);
        if (user != null) {
            log.info("Fetching requests for user ID: {}", user.getId());
            return serviceRequestRepository.findByUserId(user.getId());
        }
        log.warn("Could not find authenticated user to fetch requests.");
        return new ArrayList<>();
    }

    public ServiceRequest getRequestById(Long id) {
        log.info("Fetching request by ID: {}", id);
        return serviceRequestRepository.findById(id).orElse(null);
    }

    public String makePayment(String requestId) {
        log.info("Generating mock payment link for request ID: {}", requestId);
        return "https://example.com/payment/" + requestId;
    }

    private User getCurrentUser(String email) {
        if (email != null) {
            return userRepository.findByEmail(email).orElse(null);
        }
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(userEmail).orElse(null);
    }
}
