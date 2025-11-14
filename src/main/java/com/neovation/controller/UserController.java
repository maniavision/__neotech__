package com.neovation.controller;

import com.neovation.model.User;
import com.neovation.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
//@CrossOrigin
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}