package com.template.springboottemplate.service;

import com.template.springboottemplate.config.JwtTokenProvider;
import com.template.springboottemplate.dto.AuthRequest;
import com.template.springboottemplate.dto.NewUserDto;
import com.template.springboottemplate.dto.ResetPasswordDto;
import com.template.springboottemplate.model.EmailVerificationToken;
import com.template.springboottemplate.model.PasswordResetToken;
import com.template.springboottemplate.model.User;
import com.template.springboottemplate.repository.EmailVerificationTokenRepository;
import com.template.springboottemplate.repository.PasswordResetTokenRepository;
import com.template.springboottemplate.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {
    final private UserRepository userRepo;
    final private EmailVerificationTokenRepository evtRepo;
    final private PasswordResetTokenRepository prtRepo;
    final private PasswordEncoder encoder;
    final private JavaMailSender mailSender;
    final private TemplateEngine templateEngine;

    // Character set for password generation
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    public UserService(UserRepository userRepo, EmailVerificationTokenRepository evtRepo, PasswordResetTokenRepository prtRepo, PasswordEncoder encoder, JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.userRepo = userRepo;
        this.evtRepo = evtRepo;
        this.prtRepo = prtRepo;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public User register(NewUserDto dto) {
        if (userRepo.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Email already in use");
        }
        User user = new User();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(encoder.encode(dto.getPassword()));
        user = userRepo.save(user);

        String token = UUID.randomUUID().toString();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setToken(token);
        evt.setUser(user);
        evt.setExpiryDate(LocalDateTime.now().plusHours(24));
        evtRepo.save(evt);

        String link = "http://localhost:8080/api/auth/confirm?token=" + token;
        // --- 3. Use the template ---
        Context context = new Context();
        context.setVariable("title", "Confirm Your Email");
        context.setVariable("bodyText", "Welcome! Please click the button below to verify your email address and activate your account.");
        context.setVariable("buttonText", "Verify Email");
        context.setVariable("linkUrl", link);

        String htmlBody = templateEngine.process("email-template.html", context);

        sendEmail(user.getEmail(), "Confirm your email", htmlBody);

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
        EmailVerificationToken evt = evtRepo.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));
        if (evt.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }
        User user = evt.getUser();

        // Generate a new password
        String newPassword = generateRandomPassword();

        // Update user with new password and enable them
        user.setPassword(encoder.encode(newPassword));
        user.setEnabled(true);
        userRepo.save(user);

        // Send email with the new password
        Context context = new Context();
        context.setVariable("title", "Registration Confirmed");
        context.setVariable("bodyText", "Your account has been successfully activated. Your temporary password is provided below. Please log in and change it.");
        context.setVariable("infoText", "Your temporary password is: " + newPassword );
        // We set linkUrl to null so the button doesn't appear
        context.setVariable("linkUrl", null);

        String htmlBody = templateEngine.process("email-template.html", context);
        sendEmail(user.getEmail(),
                "Registration Confirmed",
                htmlBody);

        // Delete the token
        evtRepo.delete(evt);
    }

    public String login(AuthRequest dto, AuthenticationManager authManager, JwtTokenProvider jwtProvider) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));
        return jwtProvider.generateToken(auth);
    }

    public void requestPasswordReset(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String token = UUID.randomUUID().toString();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setToken(token);
        prt.setUser(user);
        prt.setExpiryDate(LocalDateTime.now().plusHours(1));
        prtRepo.save(prt);

        String link = "http://localhost:8080/api/auth/reset-password?token=" + token;
        Context context = new Context();
        context.setVariable("title", "Password Reset Request");
        context.setVariable("bodyText", "You requested to reset your password. Click the button below to proceed.");
        context.setVariable("buttonText", "Reset Password");
        context.setVariable("linkUrl", link);

        String htmlBody = templateEngine.process("email-template.html", context);
        sendEmail(email, "Reset Your Password", htmlBody);
    }

    public void resetPassword(ResetPasswordDto dto) {
        PasswordResetToken prt = prtRepo.findByToken(dto.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid token"));
        if (prt.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }
        User user = prt.getUser();
        user.setPassword(encoder.encode(dto.getNewPassword()));
        userRepo.save(user);
        prtRepo.delete(prt);
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
        } catch (MessagingException e) {
            // Handle exception (e.g., log it)
            throw new RuntimeException("Failed to send email", e);
        }
    }
}