package com.template.springboottemplate.controller;

import com.template.springboottemplate.model.User;
import com.template.springboottemplate.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
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
        userService.updateProfileImage(user.getId(), file);
        return ResponseEntity.ok().body("{\"message\": \"Profile image updated successfully\"}");
    }
}