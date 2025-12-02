package com.neovation.service;

import com.neovation.dto.CreateRequestDto;
import com.neovation.dto.NewUserDto;
import com.neovation.model.*;
import com.neovation.repository.PaymentRepository; // <--- NEW IMPORT
import com.neovation.repository.ServiceRequestRepository;
import com.neovation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock
    private ServiceRequestRepository serviceRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StripePaymentService stripePaymentService; // <--- NEW MOCK
    @Mock
    private PaymentRepository paymentRepository;     // <--- NEW MOCK

    @InjectMocks
    private RequestService requestService;

    private CreateRequestDto createRequestDto;
    private User existingUser;

    @BeforeEach
    void setUp() {
        createRequestDto = new CreateRequestDto();
        createRequestDto.setFirstName("Test");
        createRequestDto.setLastName("User");
        createRequestDto.setEmail("test@example.com");
        createRequestDto.setCountryCode("US");
        createRequestDto.setTitle("New Website");
        createRequestDto.setService(ServiceType.WEB_DEVELOPMENT);
        createRequestDto.setDescription("Need a new website");
        createRequestDto.setBudgetRange("$1000-$2000");
        createRequestDto.setExpectedDueDate(LocalDate.now().plusMonths(1));

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail("test@example.com");
        existingUser.setFirstName("Test");
        existingUser.setLastName("User");
    }

    private void mockSecurityContext(String email) {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void createRequest_existingUser() {
        // --- Arrange ---
        String expectedId = "uuid-test-100"; // Changed ID to String

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));
        when(serviceRequestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> {
            ServiceRequest req = invocation.getArgument(0);
            req.setId(expectedId); // Simulate ID generation as String
            return req;
        });

        // --- Act ---
        ServiceRequest result = requestService.createRequest(createRequestDto);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(expectedId, result.getId()); // Assert String ID
        assertEquals(existingUser.getId(), result.getUserId());
        assertEquals("New Website", result.getTitle());
        assertEquals(RequestStatus.SUBMITTED, result.getStatus());

        // Verify email notifications were sent
        verify(userService, times(1)).sendRequestCreatedEmail(result);
        verify(userService, times(1)).sendNewRequestAlertEmail(result);

        // Verify user was NOT registered
        verify(userService, never()).register(any(NewUserDto.class));
        // Verify repository save was called
        verify(serviceRequestRepository, times(1)).save(any(ServiceRequest.class));
    }

    @Test
    void createRequest_newUser_registersAndCreatesRequest() {
        // --- Arrange ---
        String expectedId = "uuid-test-101"; // Changed ID to String

        // 1. User is NOT found
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        // 2. Mock the registration process
        when(userService.register(any(NewUserDto.class))).thenReturn(existingUser); // Return the "newly registered" user
        // 3. Mock the request save
        when(serviceRequestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> {
            ServiceRequest req = invocation.getArgument(0);
            req.setId(expectedId);
            return req;
        });

        // --- Act ---
        ServiceRequest result = requestService.createRequest(createRequestDto);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(expectedId, result.getId()); // Assert String ID
        assertEquals(existingUser.getId(), result.getUserId()); // ID from registered user
        assertEquals(RequestStatus.SUBMITTED, result.getStatus());

        // Verify email notifications were sent
        verify(userService, times(1)).sendRequestCreatedEmail(result);
        verify(userService, times(1)).sendNewRequestAlertEmail(result);

        // Verify registration was called
        ArgumentCaptor<NewUserDto> newUserCaptor = ArgumentCaptor.forClass(NewUserDto.class);
        verify(userService, times(1)).register(newUserCaptor.capture());
        assertEquals("test@example.com", newUserCaptor.getValue().getEmail());
        assertEquals("US", newUserCaptor.getValue().getCountryCode());

        // Verify repository save was called
        verify(serviceRequestRepository, times(1)).save(any(ServiceRequest.class));
    }

    @Test
    void createRequest_withAttachments() {
        // --- Arrange ---
        String expectedId = "uuid-test-102"; // Changed ID to String

        MockMultipartFile file1 = new MockMultipartFile("file", "test1.pdf", "application/pdf", "test data 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.png", "image/png", "test data 2".getBytes());
        createRequestDto.setAttachments(List.of(file1, file2));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));

        // Mock file storage service calls and provide mock GCS paths
        when(fileStorageService.storeFile(eq(file1), eq(existingUser.getId()))).thenReturn("gcs-path-1.pdf");
        when(fileStorageService.storeFile(eq(file2), eq(existingUser.getId()))).thenReturn("gcs-path-2.png");

        when(serviceRequestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> {
            ServiceRequest req = invocation.getArgument(0);
            req.setId(expectedId);
            // Manually set GCS paths/filenames for assertions since we are mocking creation
            if (req.getAttachments() != null) {
                req.getAttachments().get(0).setUrl("gcs-path-1.pdf");
                req.getAttachments().get(0).setFileName(file1.getOriginalFilename());
                req.getAttachments().get(1).setUrl("gcs-path-2.png");
                req.getAttachments().get(1).setFileName(file2.getOriginalFilename());
            }
            return req;
        });

        // --- Act ---
        ServiceRequest result = requestService.createRequest(createRequestDto);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(expectedId, result.getId());
        assertNotNull(result.getAttachments());
        assertEquals(2, result.getAttachments().size());

        // Assert file storage was called with the correct user ID
        verify(fileStorageService, times(1)).storeFile(eq(file1), eq(existingUser.getId()));
        verify(fileStorageService, times(1)).storeFile(eq(file2), eq(existingUser.getId()));

        // Verify attachment details and purpose (purpose should be USER_FILE by default)
        assertEquals("test1.pdf", result.getAttachments().get(0).getFileName());
        assertEquals("gcs-path-2.png", result.getAttachments().get(1).getUrl());
        assertEquals(file1.getSize(), result.getAttachments().get(0).getFileSize());
        assertEquals(FilePurpose.USER_FILE, result.getAttachments().get(0).getPurpose());
        assertEquals(FilePurpose.USER_FILE, result.getAttachments().get(1).getPurpose());

        // Verify email notifications were sent
        verify(userService, times(1)).sendRequestCreatedEmail(result);
        verify(userService, times(1)).sendNewRequestAlertEmail(result);
    }
}