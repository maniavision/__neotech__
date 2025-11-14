package com.neovation.controller;

import com.neovation.config.JwtTokenProvider;
import com.neovation.dto.AuthRequest;
import com.neovation.dto.AuthResponse;
import com.neovation.dto.NewUserDto;
import com.neovation.dto.ResetPasswordDto;
import com.neovation.model.User;
import com.neovation.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin
@Controller
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    final private UserService userService;
    final private AuthenticationManager authManager;
    final private JwtTokenProvider jwtProvider;

    public AuthController(UserService userService, AuthenticationManager authManager, JwtTokenProvider jwtProvider) {
        this.userService = userService;
        this.authManager = authManager;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/register")
    @ResponseBody
    public ResponseEntity<?> register(@RequestBody @Valid NewUserDto dto) {
        log.info("Received API request to /register for email: {}", dto.getEmail());
        userService.register(dto);
        return ResponseEntity.ok("Registration successful. Check your email.");
    }

    @GetMapping("/confirm")
    public String confirmEmail(@RequestParam String token) {
        log.info("Received API request to /confirm with token: {}", token);
        userService.confirmEmail(token);
        log.info("Email confirmation successful for token: {}", token);
        return "confirmation-success";
    }

    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam String token) {
        log.info("Received API request to /reset-password with token: {}", token);
        userService.confirmEmail(token);
        log.info("Email confirmation successful for token: {}", token);
        return "confirmation-success";
    }

    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest dto) {
        log.info("Received API request to /login for email: {}", dto.getEmail());
        String jwt = userService.login(dto, authManager, jwtProvider);
        User user = userService.getUserByEmail(dto.getEmail());
        return ResponseEntity.ok(new AuthResponse(jwt, user));
    }

    @PostMapping("/forgot-password")
    @ResponseBody
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> payload) {
        String email = payload.get("email"); // Get email from the JSON body
        log.info("Received API request to /forgot-password for email: {}", email);
        userService.requestPasswordReset(email);
        return ResponseEntity.ok("Password reset email sent.");
    }

    @PostMapping("/reset-password")
    @ResponseBody
    public ResponseEntity<?> resetPassword(@RequestBody @Valid ResetPasswordDto dto) {
        log.info("Received API request to /reset-password with token: {}", dto.getToken());
        userService.resetPassword(dto);
        return ResponseEntity.ok("Password has been updated.");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseBody
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        log.error("An unexpected error occurred in AuthController: {}", ex.getMessage(), ex);
        // You can return a more user-friendly error message
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}

