package com.neovation.service;

import com.neovation.dto.CreateRequestDto;
import com.neovation.dto.NewUserDto;
import com.neovation.model.*;
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
        // User is found
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));
        // Mock the save operation
        when(serviceRequestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> {
            ServiceRequest req = invocation.getArgument(0);
            req.setId(100L); // Simulate ID generation
            return req;
        });

        // --- Act ---
        ServiceRequest result = requestService.createRequest(createRequestDto);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(existingUser.getId(), result.getUserId());
        assertEquals(existingUser.getEmail(), result.getUserEmail());
        assertEquals("New Website", result.getTitle());
        assertEquals(RequestStatus.SUBMITTED, result.getStatus());

        // Verify user was NOT registered
        verify(userService, never()).register(any(NewUserDto.class));
        // Verify repository save was called
        verify(serviceRequestRepository, times(1)).save(any(ServiceRequest.class));
    }

    @Test
    void createRequest_newUser_registersAndCreatesRequest() {
        // --- Arrange ---
        // 1. User is NOT found
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        // 2. Mock the registration process
        when(userService.register(any(NewUserDto.class))).thenReturn(existingUser); // Return the "newly registered" user
        // 3. Mock the request save
        when(serviceRequestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> {
            ServiceRequest req = invocation.getArgument(0);
            req.setId(101L);
            return req;
        });

        // --- Act ---
        ServiceRequest result = requestService.createRequest(createRequestDto);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals(existingUser.getId(), result.getUserId()); // ID from registered user
        assertEquals(existingUser.getEmail(), result.getUserEmail());
        assertEquals(RequestStatus.SUBMITTED, result.getStatus());

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
        MockMultipartFile file1 = new MockMultipartFile("file", "test1.pdf", "application/pdf", "test data 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.png", "image/png", "test data 2".getBytes());
        createRequestDto.setAttachments(List.of(file1, file2));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));
        when(fileStorageService.storeFile(file1)).thenReturn("uuid-test1.pdf");
        when(fileStorageService.storeFile(file2)).thenReturn("uuid-test2.png");

        when(serviceRequestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> {
            ServiceRequest req = invocation.getArgument(0);
            req.setId(102L);
            return req;
        });

        // --- Act ---
        ServiceRequest result = requestService.createRequest(createRequestDto);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(102L, result.getId());
        assertNotNull(result.getAttachments());
        assertEquals(2, result.getAttachments().size());
        assertEquals("uuid-test1.pdf", result.getAttachments().get(0).getFileName());
        assertEquals("uuid-test2.png", result.getAttachments().get(1).getFileName());
        assertEquals(file1.getSize(), result.getAttachments().get(0).getFileSize());
        assertEquals(file2.getContentType(), result.getAttachments().get(1).getFileType());

        // Verify file storage was called for each file
        verify(fileStorageService, times(2)).storeFile(any(MultipartFile.class));
        verify(serviceRequestRepository, times(1)).save(any(ServiceRequest.class));
    }

    @Test
    void getUserRequests_success() {
        // --- Arrange ---
        // 1. Mock the security context to simulate a logged-in user
        mockSecurityContext("test@example.com");
        // 2. Mock the user lookup from the security context
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));
        // 3. Mock the repository call
        ServiceRequest req1 = new ServiceRequest();
        req1.setId(1L);
        req1.setUserId(existingUser.getId());
        ServiceRequest req2 = new ServiceRequest();
        req2.setId(2L);
        req2.setUserId(existingUser.getId());
        when(serviceRequestRepository.findByUserId(existingUser.getId())).thenReturn(List.of(req1, req2));

        // --- Act ---
        List<ServiceRequest> results = requestService.getUserRequests();

        // --- Assert ---
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(existingUser.getId(), results.get(0).getUserId());
    }
}