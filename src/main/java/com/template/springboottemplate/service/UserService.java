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
import org.springframework.beans.BeanUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    // Character set for password generation
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    public UserService(UserRepository userRepo, EmailVerificationTokenRepository evtRepo, PasswordResetTokenRepository prtRepo, PasswordEncoder encoder, JavaMailSender mailSender) {
        this.userRepo = userRepo;
        this.evtRepo = evtRepo;
        this.prtRepo = prtRepo;
        this.encoder = encoder;
        this.mailSender = mailSender;
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
        sendEmail(user.getEmail(), "Confirm your email", "Click to confirm: " + link);

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
        sendEmail(user.getEmail(),
                "Registration Confirmed",
                "Your account has been activated. Your new password is: " + newPassword +
                        "\n\nPlease use this password to log in and change it if you wish.");

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
        sendEmail(email, "Reset Your Password", "Click to reset: " + link);
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

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}