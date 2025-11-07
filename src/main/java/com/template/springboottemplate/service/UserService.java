package com.template.springboottemplate.service;

import com.template.springboottemplate.config.JwtTokenProvider;
import com.template.springboottemplate.dto.AuthRequest;
import com.template.springboottemplate.dto.NewUserDto;
import com.template.springboottemplate.dto.ResetPasswordDto;
import com.template.springboottemplate.model.Country;
import com.template.springboottemplate.model.EmailVerificationToken;
import com.template.springboottemplate.model.PasswordResetToken;
import com.template.springboottemplate.model.User;
import com.template.springboottemplate.repository.CountryRepository;
import com.template.springboottemplate.repository.EmailVerificationTokenRepository;
import com.template.springboottemplate.repository.PasswordResetTokenRepository;
import com.template.springboottemplate.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    @Value("${app.frontend.url}")
    private String frontendUrl;
    final private UserRepository userRepo;
    final private EmailVerificationTokenRepository evtRepo;
    final private PasswordResetTokenRepository prtRepo;
    final private PasswordEncoder encoder;
    final private JavaMailSender mailSender;
    final private TemplateEngine templateEngine;
    final private MessageSource messageSource;
    final private FileStorageService fileStorageService;
    final private CountryRepository countryRepo;

    // Character set for password generation
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    public UserService(UserRepository userRepo, EmailVerificationTokenRepository evtRepo, PasswordResetTokenRepository prtRepo, PasswordEncoder encoder, JavaMailSender mailSender, TemplateEngine templateEngine, MessageSource messageSource, FileStorageService fileStorageService, CountryRepository countryRepo) {
        this.userRepo = userRepo;
        this.evtRepo = evtRepo;
        this.prtRepo = prtRepo;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
        this.fileStorageService = fileStorageService;
        this.countryRepo = countryRepo;
    }

    public User register(NewUserDto dto) {
        // === Determine Locale ===
        // This is a placeholder. You should get the locale from the request
        // (e.g., from an 'Accept-Language' header in the controller or a 'lang' field in the DTO).
        // For this example, we'll default to English.
        Locale locale = Locale.FRENCH;
        log.info("Attempting to register new user with email: {}", dto.getEmail());
        // To test French, use: Locale locale = Locale.FRENCH;
        // ========================

        if (userRepo.findByEmail(dto.getEmail()).isPresent()) {
            log.warn("Registration failed: Email already in use: {}", dto.getEmail());
            throw new RuntimeException("Email already in use");
        }

        Country country = countryRepo.findById(dto.getCountryCode())
                .orElseThrow(() -> {
                    log.error("Registration failed: Invalid country code provided: {}", dto.getCountryCode());
                    return new RuntimeException("Invalid country code");
                });
        log.debug("Verified country: {} ({})", country.getName(), country.getCode());

        User user = new User();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(encoder.encode(dto.getPassword()));
        user = userRepo.save(user);
        log.info("Successfully saved new user with ID: {}", user.getId());

        String token = UUID.randomUUID().toString();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setToken(token);
        evt.setUser(user);
        evt.setExpiryDate(LocalDateTime.now().plusHours(24));
        evtRepo.save(evt);
        log.info("Generated and saved email verification token for user {}", user.getId());

        String link = "http://localhost:8080/api/auth/confirm?token=" + token;
        // --- Use MessageSource to get translated text ---
        String title = messageSource.getMessage("email.register.title", null, locale);
        String bodyText = messageSource.getMessage("email.register.body", null, locale);
        String buttonText = messageSource.getMessage("email.register.button", null, locale);

        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("bodyText", bodyText);
        context.setVariable("buttonText", buttonText);
        context.setVariable("linkUrl", link);
        context.setVariable("baseUrl", frontendUrl);

        String htmlBody = templateEngine.process("email-template.html", context);

        sendEmail(user.getEmail(), title, htmlBody);
        log.info("Sent registration email to {}", user.getEmail());
        return user;
    }

    /**
     * Generates a random 12-character password using alphanumeric chars and symbols.
     */
    private String generateRandomPassword() {
        return RANDOM.ints(PASSWORD_LENGTH, 0, PASSWORD_CHARS.length())
                .mapToObj(PASSWORD_CHARS::charAt)
                .map(Object::toString)
                .collect(Collectors.joining());
    }

    public void confirmEmail(String token) {

        Locale locale = Locale.FRENCH;
        log.info("Attempting to confirm email with token: {}", token);

        EmailVerificationToken evt = evtRepo.findByToken(token)
                .orElseThrow(() -> {
                    log.warn("Invalid token used for email confirmation: {}", token);
                    return new RuntimeException("Invalid token");
                });

        if (evt.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.warn("Expired token used for email confirmation: {}", token);
            evtRepo.delete(evt);
            throw new RuntimeException("Token expired");
        }
        User user = evt.getUser();

        if (user.isEnabled()) {
            log.warn("Email confirmation attempted for already enabled user: {}", user.getEmail());
            evtRepo.delete(evt); // Clean up the used token
            return; // Exit the method early
        }

        // Generate a new password
        String newPassword = generateRandomPassword();

        // Update user with new password and enable them
        user.setPassword(encoder.encode(newPassword));
        user.setEnabled(true);
        userRepo.save(user);
        log.info("Successfully enabled user and set new password for: {}", user.getEmail());
        // --- Use MessageSource ---
        String title = messageSource.getMessage("email.confirm.title", null, locale);
        String bodyText = messageSource.getMessage("email.confirm.body", null, locale);
        // Pass the new password as an argument for the {0} placeholder
        Object[] args = { newPassword };
        String infoText = messageSource.getMessage("email.confirm.infotext", args, locale);

        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("bodyText", bodyText);
        context.setVariable("infoText", infoText);
        context.setVariable("linkUrl", null);
        context.setVariable("baseUrl", frontendUrl);

        String htmlBody = templateEngine.process("email-template.html", context);
        sendEmail(user.getEmail(),
                title,
                htmlBody);

        log.info("Sent temporary password email to: {}", user.getEmail());
        evtRepo.delete(evt);
        log.info("Deleted used email verification token: {}", token);
    }

    public String login(AuthRequest dto, AuthenticationManager authManager, JwtTokenProvider jwtProvider) {
        log.info("Attempting login for user: {}", dto.getEmail());
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));
        log.info("Login successful, token generated for: {}", dto.getEmail());
        return jwtProvider.generateToken(auth);
    }

    public void requestPasswordReset(String email) {
        Locale locale = Locale.ENGLISH;
        log.info("Processing password reset request for email: {}", email);
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Password reset request for non-existent user: {}", email);
                    return new RuntimeException("User not found");
                });
        String token = UUID.randomUUID().toString();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setToken(token);
        prt.setUser(user);
        prt.setExpiryDate(LocalDateTime.now().plusHours(1));
        prtRepo.save(prt);
        log.info("Saved password reset token for user: {}", email);
        String link = "http://localhost:8080/api/auth/reset-password?token=" + token;
        // --- Use MessageSource ---
        String title = messageSource.getMessage("email.reset.title", null, locale);
        String bodyText = messageSource.getMessage("email.reset.body", null, locale);
        String buttonText = messageSource.getMessage("email.reset.button", null, locale);

        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("bodyText", bodyText);
        context.setVariable("buttonText", buttonText);
        context.setVariable("linkUrl", link);
        context.setVariable("baseUrl", frontendUrl);

        String htmlBody = templateEngine.process("email-template.html", context);
        sendEmail(email, title, htmlBody);
        log.info("Sent password reset email to: {}", email);
    }

    public void resetPassword(ResetPasswordDto dto) {
        log.info("Attempting to reset password with token: {}", dto.getToken());
        PasswordResetToken prt = prtRepo.findByToken(dto.getToken())
                .orElseThrow(() -> {
                    log.warn("Invalid token used for password reset: {}", dto.getToken());
                    return new RuntimeException("Invalid token");
                });

        if (prt.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.warn("Expired token used for password reset: {}", dto.getToken());
            throw new RuntimeException("Token expired");
        }

        User user = prt.getUser();
        user.setPassword(encoder.encode(dto.getNewPassword()));
        userRepo.save(user);
        prtRepo.delete(prt);
        log.info("Successfully reset password for user: {}", user.getEmail());
    }

    public User getUserByEmail(String email) {
        return this.userRepo.findByEmail(email).orElseThrow();
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8"); // true = multipart

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML

            // Add the logo as an inline resource
            // "logo" is the Content-ID (CID) used in the <img> tag (src="cid:logo")
            ClassPathResource logo = new ClassPathResource("static/images/logo.png");
            helper.addInline("logo", logo);
            mailSender.send(mimeMessage);
            log.info("Email sent successfully to {} with subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {} with subject: {}", to, subject, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void updateProfileImage(Long userId, MultipartFile file) {
        log.info("Updating profile image for user ID: {}", userId);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String fileName = fileStorageService.storeFile(file);
        user.setProfileImage(fileName);
        userRepo.save(user);
        log.info("Successfully updated profile image for user ID: {}", userId);
    }
}