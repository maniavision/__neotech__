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

    // Add to src/main/java/com/neovation/controller/UserController.java

    @DeleteMapping("/profile-image")
    public ResponseEntity<?> deleteProfileImage(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }

        try {
            userService.deleteProfileImage(userDetails.getUsername());
            return ResponseEntity.ok().body(Map.of("message", "Profile image deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete profile image for user: {}", userDetails.getUsername(), e);
            return ResponseEntity.internalServerError().body("Error deleting profile image");
        }
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

    /**
     * Generates a temporary signed URL for the user's profile picture.
     */
    @GetMapping("/me/profile-image-url")
    public ResponseEntity<?> getProfileImageUrl(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }
        try {
            String url = userService.getProfileImageUrl(userDetails.getUsername());
            if (url == null) {
                return ResponseEntity.ok(Map.of("profileImageUrl", (Object)null));
            }
            return ResponseEntity.ok(Map.of("profileImageUrl", url));
        } catch (Exception e) {
            log.error("Failed to generate signed URL for user: {}", userDetails.getUsername(), e);
            return ResponseEntity.internalServerError().body("Error generating URL");
        }
    }

    /**
     * Generates a temporary signed URL for a specific user's profile picture by ID.
     */
    @GetMapping("/{id}/profile-image-url")
    public ResponseEntity<?> getProfileImageUrlById(@PathVariable Long id) {
        log.info("Received API request for profile image URL for user ID: {}", id);
        try {
            String url = userService.getProfileImageUrl(id); // Uses the new ID-based service method
            if (url == null) {
                // Return null in the map if no image is set
                return ResponseEntity.ok(Map.of("profileImageUrl", (Object)null));
            }
            return ResponseEntity.ok(Map.of("profileImageUrl", url));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("User not found")) {
                log.warn("User ID {} not found", id);
                return ResponseEntity.notFound().build(); // HTTP 404
            }
            log.error("Failed to generate signed URL for user ID: {}", id, e);
            return ResponseEntity.internalServerError().body("Error generating URL"); // HTTP 500
        }
    }
}