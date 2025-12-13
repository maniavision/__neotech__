package com.neovation.service;

import com.neovation.config.JwtTokenProvider;
import com.neovation.model.*;
import com.neovation.repository.EmailVerificationTokenRepository;
import com.neovation.repository.PasswordResetTokenRepository;
import com.neovation.repository.UserRepository;
import com.neovation.dto.AuthRequest;
import com.neovation.dto.NewUserDto;
import com.neovation.dto.ResetPasswordDto;
import com.neovation.repository.CountryRepository;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.backend.url}")
    private String backendUrl;

    @Value("${app.internal.support-email}") // <--- NEW INJECTION
    private String internalSupportEmail;

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
        Locale locale = Locale.ENGLISH;
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
        user.setCountry(country);
        user.setPassword(encoder.encode(dto.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user = userRepo.save(user);
        log.info("Successfully saved new user with ID: {}", user.getId());

        String token = UUID.randomUUID().toString();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setToken(token);
        evt.setUser(user);
        evt.setExpiryDate(LocalDateTime.now().plusHours(24));
        evtRepo.save(evt);
        log.info("Generated and saved email verification token for user {}", user.getId());

        String link = String.format("%s/api/auth/confirm?token=%s", backendUrl, token);
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

        Locale locale = Locale.ENGLISH;
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
        String link = String.format("%s/api/auth/reset-password?token=%s", backendUrl, token);
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

    public void sendEmail(String to, String subject, String htmlBody) {
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

//    public void updateProfileImage(Long userId, MultipartFile file) {
//        log.info("Updating profile image for user ID: {}", userId);
//        User user = userRepo.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//        String gcsPath = fileStorageService.storeFile(file, userId);
//        // Store the GCS path in the profileImage field
//        user.setProfileImage(gcsPath);
//        userRepo.save(user);
//        log.info("Successfully updated profile image for user ID: {}", userId);
//    }

    public User saveUser(User user) {
        return this.userRepo.save(user);
    }

    public Optional<User> findUserById(Long id) {
        return this.userRepo.findById(id);
    }

    public String updateProfileImage(Long userId, MultipartFile file) {
        log.info("Updating profile image for user ID: {}", userId);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Get the old URL *before* changing it
        String oldProfileUrl = user.getProfileImage();

        try {
            // The folder name is the user's ID
            String folderName = String.valueOf(user.getId());

            // 2. Upload the new file and get its public URL
            String publicUrl = fileStorageService.uploadFile(file, folderName, file.getOriginalFilename());

            // 3. Save the new public URL to the user's profile
            user.setProfileImage(publicUrl);
            userRepo.save(user);
            log.info("Successfully updated profile image for user ID: {}. URL: {}", userId, publicUrl);

            // 4. Delete the old file *after* the new one is saved
            if (oldProfileUrl != null && !oldProfileUrl.isEmpty()) {
                log.info("Deleting old profile image: {}", oldProfileUrl);
                // Use the new helper method
                fileStorageService.deleteFileFromUrl(oldProfileUrl);
            }

            // Return the URL to the controller
            return publicUrl;
        } catch (IOException e) {
            log.error("Failed to upload profile image for user ID: {}", userId, e);
            // If upload fails, the old URL is not deleted and the exception is thrown
            throw new RuntimeException("Could not store file: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * Gets a signed URL for the currently authenticated user's profile picture.
     * @param userEmail The email of the authenticated user.
     * @return A signed URL, or null if no image is set.
     */
    public String getProfileImageUrl(String userEmail) {
        log.debug("Generating profile image URL for user: {}", userEmail);
        User user = getUserByEmail(userEmail);

        String blobPath = user.getProfileImage();
        if (blobPath == null || blobPath.isEmpty()) {
            return null; // No profile picture set
        }

        return fileStorageService.generateSignedProfileUrl(blobPath);
    }

    /**
     * Gets a list of all users, excluding the user identified by the email,
     * with optional filtering by name or email.
     * @param emailToExclude The email of the user to exclude from the list (the caller).
     * @param query Optional search term for first name, last name, or email.
     * @return A filtered list of User objects.
     */
    public List<User> getAllUsersExcept(String emailToExclude, String query) {
        List<User> results;

        if (query != null && !query.trim().isEmpty()) {
            log.debug("Fetching users matching query: {} excluding: {}", query, emailToExclude);

            // Search by query in firstName OR lastName OR email
            // We pass the same query string three times to match the repository method signature
            results = userRepo.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    query, query, query);
        } else {
            log.debug("Fetching all users, excluding: {}", emailToExclude);
            // Default to fetching all users if no query provided
            results = userRepo.findAll();
        }

        // Always filter out the user who is making the request
        return results.stream()
                .filter(user -> !user.getEmail().equals(emailToExclude))
                .collect(Collectors.toList());
    }

    /**
     * Gets a signed URL for a specific user's profile picture by ID.
     * This method can be used by any authenticated user (e.g., ADMIN, STAFF, or another USER)
     * to view a profile picture, given the user ID.
     * * @param userId The ID of the user whose profile image is requested.
     * @return A signed URL, or null if no image is set.
     */
    public String getProfileImageUrl(Long userId) {
        log.debug("Generating profile image URL for user ID: {}", userId);

        // Find user by ID, throws RuntimeException if not found
        User user = findUserById(userId)
                .orElseThrow(() -> new RuntimeException("User not found")); // Reuses existing findUserById(Long id)

        String blobPath = user.getProfileImage();
        if (blobPath == null || blobPath.isEmpty()) {
            return null; // No profile picture set
        }

        // Delegate to FileStorageService's existing signed URL generation logic
        return fileStorageService.generateSignedProfileUrl(blobPath);
    }

    /**
     * Dedicated method for sending New Request Confirmation email.
     */
    public void sendRequestCreatedEmail(ServiceRequest request, String lang) {
        Locale locale = ("en".equalsIgnoreCase(lang)) ? Locale.ENGLISH : Locale.FRENCH;
        String to = request.getUserId() != null ?
                userRepo.findById(request.getUserId()).map(User::getEmail).orElse(null) :
                null;

        if (to == null) {
            log.warn("Skipping request created email: User ID {} not found or is null.", request.getUserId());
            return;
        }

        String title = messageSource.getMessage("email.request.created.title", null, locale);
        String bodyText = messageSource.getMessage("email.request.created.body", null, locale);
        String buttonText = messageSource.getMessage("email.request.created.button", null, locale);

        // Arguments: {0} = Request ID, {1} = Title, {2} = Service Type
        Object[] infoArgs = { request.getId(), request.getTitle(), request.getService().name() };
        String infoText = messageSource.getMessage("email.request.created.infotext", infoArgs, locale);

        // Link to view the request on the frontend
        String link = String.format("%s/requests/%s", frontendUrl, request.getId());

        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("bodyText", bodyText);
        context.setVariable("buttonText", buttonText);
        context.setVariable("infoText", infoText);
        context.setVariable("linkUrl", link);
        context.setVariable("baseUrl", frontendUrl);

        String htmlBody = templateEngine.process("email-template.html", context);
        sendEmail(to, title, htmlBody);
        log.info("Sent new request email for ID {} to user {}", request.getId(), to);
    }

    /**
     * Dedicated method for sending Proposal Uploaded email.
     */
    public void sendProposalUploadedEmail(ServiceRequest request, String lang) {
        Locale locale = ("en".equalsIgnoreCase(lang)) ? Locale.ENGLISH : Locale.FRENCH;
        String to = request.getUserId() != null ?
                userRepo.findById(request.getUserId()).map(User::getEmail).orElse(null) :
                null;

        if (to == null) {
            log.warn("Skipping proposal uploaded email: User ID {} not found or is null.", request.getUserId());
            return;
        }

        String title = messageSource.getMessage("email.proposal.uploaded.title", null, locale);
        String bodyText = messageSource.getMessage("email.proposal.uploaded.body", null, locale);
        String buttonText = messageSource.getMessage("email.proposal.uploaded.button", null, locale);

        // Arguments: {0} = Request ID, {1} = Title
        Object[] infoArgs = { request.getId(), request.getTitle() };
        String infoText = messageSource.getMessage("email.proposal.uploaded.infotext", infoArgs, locale);

        // Link to view the request/proposal on the frontend
        String link = String.format("%s/requests/%d", frontendUrl, request.getId());

        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("bodyText", bodyText);
        context.setVariable("buttonText", buttonText);
        context.setVariable("infoText", infoText);
        context.setVariable("linkUrl", link);
        context.setVariable("baseUrl", frontendUrl);

        String htmlBody = templateEngine.process("email-template.html", context);
        sendEmail(to, title, htmlBody);
        log.info("Sent proposal uploaded email for ID {} to user {}", request.getId(), to);
    }

    /**
     * Dedicated method for sending Company Alert Email about a new request.
     */
    public void sendNewRequestAlertEmail(ServiceRequest request, String lang) {
        Locale locale = ("en".equalsIgnoreCase(lang)) ? Locale.ENGLISH : Locale.FRENCH;
        String to = internalSupportEmail;

        if (to == null || to.isBlank()) {
            log.warn("Skipping internal new request alert email: internal support email is not configured.");
            return;
        }

        // Determine who submitted the request (Guest or Registered User)
        String submittedByName = "Guest";
        if (request.getUserId() != null) {
            submittedByName = userRepo.findById(request.getUserId())
                    .map(u -> u.getFirstName() + " " + u.getLastName() + " (" + u.getEmail() + ")")
                    .orElse("Unknown Registered User");
        }


        String title = messageSource.getMessage("email.internal.new.request.title", new Object[]{request.getTitle()}, locale);
        String bodyText = messageSource.getMessage("email.internal.new.request.body", null, locale);
        String buttonText = messageSource.getMessage("email.internal.new.request.button", null, locale);

        // Arguments: {1} = Request ID, {2} = Service Type, {3} = Submitted By
        Object[] infoArgs = { request.getId(), request.getService().name(), submittedByName };
        String infoText = messageSource.getMessage("email.internal.new.request.infotext", infoArgs, locale);

        // Link to view the request on the frontend (assuming admin panel path)
        String link = String.format("%s/admin/requests/%s", frontendUrl, request.getId()); // Assuming a path for Admin view

        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("bodyText", bodyText);
        context.setVariable("buttonText", buttonText);
        context.setVariable("infoText", infoText);
        context.setVariable("linkUrl", link);
        context.setVariable("baseUrl", frontendUrl);

        String htmlBody = templateEngine.process("email-template.html", context);
        sendEmail(to, title, htmlBody);
        log.info("Sent internal new request alert for ID {} to {}", request.getId(), to);
    }

    /**
     * Dedicated method for sending Payment Receipt email.
     */
    public void sendPaymentReceiptEmail(Payment payment) { // <--- MODIFIED IMPLEMENTATION
        Locale locale = Locale.ENGLISH; // Assuming English for now, locale logic can be expanded

        ServiceRequest request = payment.getServiceRequest();
        String to = payment.getEmail(); // Use the email saved on the payment record

        if (to == null || request == null) {
            log.warn("Skipping payment receipt email: Payment or associated Request is incomplete.");
            return;
        }

        // --- Get Customer Details ---
        String customerName;
        String currency = "USD"; // Assuming USD as StripePaymentService uses "usd"
        String transactionId = payment.getId().toString();

        if (request.getUserId() != null) {
            User user = userRepo.findById(request.getUserId()).orElse(null);
            if (user != null) {
                customerName = user.getFirstName() + " " + user.getLastName();
            } else {
                customerName = payment.getEmail(); // Fallback to email
            }
        } else {
            // For guest checkout
            customerName = payment.getEmail();
        }

        // --- Prepare Context for payment-receipt-template.html ---
        Context context = new Context();
        context.setVariable("baseUrl", frontendUrl);
        context.setVariable("customerName", customerName);
        context.setVariable("customerEmail", to);
        context.setVariable("paymentDate", payment.getCreatedAt());
        context.setVariable("transactionId", transactionId);
        context.setVariable("serviceName", request.getTitle());
        // Using the request description as the long-form service description
        context.setVariable("serviceDescription", request.getDescription());
        context.setVariable("currency", currency);

        // Since no tax/subtotal logic exists, set total amount as subtotal for simplicity
        // The HTML template uses these for display, so we set them to the amount paid.
        context.setVariable("subtotal", payment.getAmount());
        context.setVariable("tax", BigDecimal.ZERO);
        context.setVariable("totalAmount", payment.getAmount());

        // Hardcoded next steps for the receipt (these are specific to the new template)
        context.setVariable("stepOne", "We have successfully processed your payment for the service request.");
        context.setVariable("stepTwo", "Your request status has been updated to 'PAYMENT_RECEIVED'.");
        context.setVariable("stepThree", "Our team will now begin the work and provide an update soon via email.");

        // Link to view the request on the frontend
        String dashboardUrl = String.format("%s/requests/%s", frontendUrl, request.getId());
        context.setVariable("dashboardUrl", dashboardUrl);

        // --- Get Title from MessageSource ---
        String title = messageSource.getMessage("email.payment.receipt.title", null, locale);
        context.setVariable("title", title);

        // --- Use the dedicated receipt template ---
        String htmlBody = templateEngine.process("payment-receipt-template.html", context);
        sendEmail(to, title, htmlBody);
        log.info("Sent payment receipt email for Payment ID {} (Request ID {}) to user {}", payment.getId(), request.getId(), to);
    }
}