package com.neovation.controller;

import com.neovation.model.User;
import com.neovation.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
//@CrossOrigin
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    final private PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/profile-image")
    public ResponseEntity<?> uploadProfileImage(@AuthenticationPrincipal UserDetails userDetails,
                                                @RequestParam("file") MultipartFile file) {
        User user = userService.getUserByEmail(userDetails.getUsername());

        // This method now returns the public URL
        String publicUrl = userService.updateProfileImage(user.getId(), file);

        // Return the URL in the response body
        return ResponseEntity.ok().body(Map.of("profileImageUrl", publicUrl));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        log.info("User attempting to update user with ID: {}", id);
        return userService.findUserById(id)
                .map(user -> {
                    user.setFirstName(updatedUser.getFirstName());
                    user.setLastName(updatedUser.getLastName());
                    user.setCompanyName(updatedUser.getCompanyName());
                    user.setEmail(updatedUser.getEmail());
                    user.setPhone(updatedUser.getPhone());
//                    if (!updatedUser.getPassword().isBlank()) {
//                        user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
//                    }
//                    user.setRole(updatedUser.getRole()); // Optional: only if changing role
                    User savedUser = this.userService.saveUser(user);
                    log.info("User successfully updated user ID: {}", id);
                    return ResponseEntity.ok(savedUser);
                })
                .orElseGet(() -> {
                    log.warn("User failed to update - user not found with ID: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }
}