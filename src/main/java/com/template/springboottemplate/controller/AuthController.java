package com.template.springboottemplate.controller;

import com.template.springboottemplate.config.JwtTokenProvider;
import com.template.springboottemplate.dto.AuthRequest;
import com.template.springboottemplate.dto.AuthResponse;
import com.template.springboottemplate.dto.NewUserDto;
import com.template.springboottemplate.dto.ResetPasswordDto;
import com.template.springboottemplate.model.User;
import com.template.springboottemplate.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    final private UserService userService;
    final private AuthenticationManager authManager;
    final private JwtTokenProvider jwtProvider;

    public AuthController(UserService userService, AuthenticationManager authManager, JwtTokenProvider jwtProvider) {
        this.userService = userService;
        this.authManager = authManager;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid NewUserDto dto) {
        userService.register(dto);
        return ResponseEntity.ok("Registration successful. Check your email.");
    }

    @GetMapping("/confirm")
    public ResponseEntity<?> confirmEmail(@RequestParam String token) {
        userService.confirmEmail(token);
        return ResponseEntity.ok("Email confirmed. You can now log in.");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest dto) {
        String jwt = userService.login(dto, authManager, jwtProvider);
        User user = userService.getUserByEmail(dto.getEmail());
        return ResponseEntity.ok(new AuthResponse(jwt, user));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        userService.requestPasswordReset(email);
        return ResponseEntity.ok("Password reset email sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody @Valid ResetPasswordDto dto) {
        userService.resetPassword(dto);
        return ResponseEntity.ok("Password has been updated.");
    }
}

