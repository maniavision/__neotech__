package com.neovation.service;

import com.neovation.dto.NewUserDto;
import com.neovation.dto.ResetPasswordDto;
import com.neovation.model.Country;
import com.neovation.model.EmailVerificationToken;
import com.neovation.model.PasswordResetToken;
import com.neovation.model.User;
import com.neovation.repository.CountryRepository;
import com.neovation.repository.EmailVerificationTokenRepository;
import com.neovation.repository.PasswordResetTokenRepository;
import com.neovation.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private EmailVerificationTokenRepository evtRepo;
    @Mock
    private PasswordResetTokenRepository prtRepo;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private MessageSource messageSource;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private CountryRepository countryRepo;

    @Mock
    private MimeMessage mimeMessage; // Mock the MimeMessage for email sending

    @InjectMocks
    private UserService userService;

    private NewUserDto newUserDto;
    private User user;
    private Country country;

    @BeforeEach
    void setUp() {
        // Setup common objects for tests
        country = new Country();
        country.setCode("US");
        country.setName("United States");

        newUserDto = new NewUserDto(
                "Test", "User", "Test Inc.",
                "test@example.com", "1234567890",
                "password123", "US"
        );

        user = new User();
        user.setId(1L);
        user.setEmail(newUserDto.getEmail());
        user.setFirstName(newUserDto.getFirstName());
        user.setPassword("encodedPassword"); // Assume it's already encoded
        user.setEnabled(false);
        user.setCountry(country);
    }

    @Test
    void register_success() {
        // --- Arrange ---
        // 1. Mock email check
        when(userRepo.findByEmail(newUserDto.getEmail())).thenReturn(Optional.empty());
        // 2. Mock country lookup
        when(countryRepo.findById("US")).thenReturn(Optional.of(country));
        // 3. Mock password encoding
        when(encoder.encode(newUserDto.getPassword())).thenReturn("encodedPassword");
        // 4. Mock user save
        // We need to capture the user being saved to return it with an ID
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepo.save(userCaptor.capture())).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(1L); // Simulate ID generation
            return savedUser;
        });

        // 5. Mock email sending
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>Mock Email Body</html>");
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Mock Message");

        // --- Act ---
        User registeredUser = userService.register(newUserDto);

        // --- Assert ---
        assertNotNull(registeredUser);
        assertEquals(1L, registeredUser.getId());
        assertEquals(newUserDto.getEmail(), registeredUser.getEmail());
        assertEquals("encodedPassword", registeredUser.getPassword());
        assertFalse(registeredUser.isEnabled()); // User is not enabled until confirmed
        assertEquals(country, registeredUser.getCountry());

        // Verify that dependencies were called
        verify(userRepo, times(1)).findByEmail("test@example.com");
        verify(countryRepo, times(1)).findById("US");
        verify(userRepo, times(1)).save(any(User.class));
        verify(evtRepo, times(1)).save(any(EmailVerificationToken.class));
        verify(mailSender, times(1)).send(mimeMessage);
        verify(templateEngine, times(1)).process(eq("email-template.html"), any(Context.class));
    }

    @Test
    void register_emailExists_throwsException() {
        // --- Arrange ---
        // Mock email check to return an existing user
        when(userRepo.findByEmail(newUserDto.getEmail())).thenReturn(Optional.of(user));

        // --- Act & Assert ---
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.register(newUserDto);
        });

        assertEquals("Email already in use", exception.getMessage());

        // Verify that no save or email operations occurred
        verify(userRepo, times(1)).findByEmail("test@example.com");
        verify(countryRepo, never()).findById(anyString());
        verify(userRepo, never()).save(any(User.class));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void register_invalidCountry_throwsException() {
        // --- Arrange ---
        when(userRepo.findByEmail(newUserDto.getEmail())).thenReturn(Optional.empty());
        // Mock country lookup to return nothing
        when(countryRepo.findById("US")).thenReturn(Optional.empty());

        // --- Act & Assert ---
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.register(newUserDto);
        });

        assertEquals("Invalid country code", exception.getMessage());

        // Verify that no save or email operations occurred
        verify(userRepo, times(1)).findByEmail("test@example.com");
        verify(countryRepo, times(1)).findById("US");
        verify(userRepo, never()).save(any(User.class));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }


    @Test
    void confirmEmail_success() {
        // --- Arrange ---
        String token = "valid-token";
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setToken(token);
        evt.setUser(user);
        evt.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(evtRepo.findByToken(token)).thenReturn(Optional.of(evt));
        when(encoder.encode(anyString())).thenReturn("newEncodedPassword");

        // Mock email sending
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>Mock Email Body</html>");
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Mock Message");
        // Specifically mock the info text message with placeholder
        when(messageSource.getMessage(eq("email.confirm.infotext"), any(Object[].class), any(Locale.class)))
                .thenReturn("Your temporary password is: newRandomPass");

        // --- Act ---
        userService.confirmEmail(token);

        // --- Assert ---
        // 1. Verify user is enabled and has new password
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertTrue(savedUser.isEnabled());
        assertEquals("newEncodedPassword", savedUser.getPassword());

        // 2. Verify token is deleted
        verify(evtRepo, times(1)).delete(evt);

        // 3. Verify email was sent
        verify(mailSender, times(1)).send(mimeMessage);
        verify(templateEngine, times(1)).process(eq("email-template.html"), any(Context.class));
    }

    @Test
    void confirmEmail_invalidToken_throwsException() {
        // --- Arrange ---
        String token = "invalid-token";
        when(evtRepo.findByToken(token)).thenReturn(Optional.empty());

        // --- Act & Assert ---
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.confirmEmail(token);
        });
        assertEquals("Invalid token", exception.getMessage());

        // Verify no user or token modification occurred
        verify(userRepo, never()).save(any(User.class));
        verify(evtRepo, never()).delete(any(EmailVerificationToken.class));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void confirmEmail_expiredToken_throwsException() {
        // --- Arrange ---
        String token = "expired-token";
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setToken(token);
        evt.setUser(user);
        evt.setExpiryDate(LocalDateTime.now().minusHours(1)); // Expired!

        when(evtRepo.findByToken(token)).thenReturn(Optional.of(evt));

        // --- Act & Assert ---
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.confirmEmail(token);
        });
        assertEquals("Token expired", exception.getMessage());

        // Verify the expired token was deleted
        verify(evtRepo, times(1)).delete(evt);

        // Verify no user modification or email sending occurred
        verify(userRepo, never()).save(any(User.class));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void confirmEmail_userAlreadyEnabled_skipsPasswordReset() {
        // --- Arrange ---
        user.setEnabled(true); // User is already enabled
        String token = "valid-token-enabled-user";
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setToken(token);
        evt.setUser(user);
        evt.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(evtRepo.findByToken(token)).thenReturn(Optional.of(evt));

        // --- Act ---
        userService.confirmEmail(token);

        // --- Assert ---
        // Verify user was NOT saved (no password change)
        verify(userRepo, never()).save(any(User.class));
        // Verify token WAS deleted (it's been used)
        verify(evtRepo, times(1)).delete(evt);
        // Verify NO email was sent
        verify(mailSender, never()).send(any(MimeMessage.class));
    }


    @Test
    void resetPassword_success() {
        // --- Arrange ---
        String token = "valid-reset-token";
        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setToken(token);
        dto.setNewPassword("newStrongPassword123");

        PasswordResetToken prt = new PasswordResetToken();
        prt.setToken(token);
        prt.setUser(user);
        prt.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(prtRepo.findByToken(token)).thenReturn(Optional.of(prt));
        when(encoder.encode(dto.getNewPassword())).thenReturn("encodedNewPassword");

        // --- Act ---
        userService.resetPassword(dto);

        // --- Assert ---
        // 1. Verify user password was updated
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("encodedNewPassword", savedUser.getPassword());

        // 2. Verify token was deleted
        verify(prtRepo, times(1)).delete(prt);
    }

    @Test
    void resetPassword_invalidToken_throwsException() {
        // --- Arrange ---
        String token = "invalid-token";
        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setToken(token);
        dto.setNewPassword("newStrongPassword123");

        when(prtRepo.findByToken(token)).thenReturn(Optional.empty());

        // --- Act & Assert ---
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.resetPassword(dto);
        });
        assertEquals("Invalid token", exception.getMessage());

        verify(userRepo, never()).save(any(User.class));
        verify(prtRepo, never()).delete(any(PasswordResetToken.class));
    }

    @Test
    void getProfileImageUrl_success() {
        // Arrange
        String blobPath = "1/profile-image.png";
        String signedUrl = "https://signed.url/image.png";
        user.setProfileImage(blobPath);

        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(fileStorageService.generateSignedProfileUrl(blobPath)).thenReturn(signedUrl);

        // Act
        String result = userService.getProfileImageUrl("test@example.com");

        // Assert
        assertEquals(signedUrl, result);
        verify(userRepo, times(1)).findByEmail("test@example.com");
        verify(fileStorageService, times(1)).generateSignedProfileUrl(blobPath);
    }

    @Test
    void getProfileImageUrl_noImageSet_returnsNull() {
        // Arrange
        user.setProfileImage(null); // No image

        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        // Act
        String result = userService.getProfileImageUrl("test@example.com");

        // Assert
        assertNull(result);
        verify(fileStorageService, never()).generateSignedProfileUrl(any());
    }

    @Test
    void updateProfileImage_newUpload_deletesOldFile() throws IOException {
        // Arrange
        String oldBlobPath = "1/old-image.png";
        String newBlobPath = "1/new-image.png";
        String newSignedUrl = "https://signed.url/new-image.png";
        user.setProfileImage(oldBlobPath); // User has an existing image

        MockMultipartFile file = new MockMultipartFile("file", "new-image.png", "image/png", "data".getBytes());

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(fileStorageService.uploadFile(any(MultipartFile.class), eq("1"), anyString())).thenReturn(newBlobPath);
        when(fileStorageService.generateSignedProfileUrl(newBlobPath)).thenReturn(newSignedUrl);

        // Act
        String result = userService.updateProfileImage(1L, file);

        // Assert
        assertEquals(newSignedUrl, result);

        // Verify user's profile image was updated in the DB
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userCaptor.capture());
        assertEquals(newBlobPath, userCaptor.getValue().getProfileImage());

        // Verify the old file was deleted
        verify(fileStorageService, times(1)).deleteFile(oldBlobPath);
        // Verify the new file was uploaded
        verify(fileStorageService, times(1)).uploadFile(any(MultipartFile.class), eq("1"), anyString());
    }

    @Test
    void updateProfileImage_firstUpload_doesNotDelete() throws IOException {
        // Arrange
        String newBlobPath = "1/new-image.png";
        String newSignedUrl = "https://signed.url/new-image.png";
        user.setProfileImage(null); // User has NO existing image

        MockMultipartFile file = new MockMultipartFile("file", "new-image.png", "image/png", "data".getBytes());

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(fileStorageService.uploadFile(any(MultipartFile.class), eq("1"), anyString())).thenReturn(newBlobPath);
        when(fileStorageService.generateSignedProfileUrl(newBlobPath)).thenReturn(newSignedUrl);

        // Act
        String result = userService.updateProfileImage(1L, file);

        // Assert
        assertEquals(newSignedUrl, result);

        // Verify user's profile image was updated
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userCaptor.capture());
        assertEquals(newBlobPath, userCaptor.getValue().getProfileImage());

        // Verify that NO file was deleted
        verify(fileStorageService, never()).deleteFile(any());
        // Verify the new file was uploaded
        verify(fileStorageService, times(1)).uploadFile(any(MultipartFile.class), eq("1"), anyString());
    }
}