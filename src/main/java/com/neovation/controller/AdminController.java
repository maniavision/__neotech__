package com.neovation.controller;

import com.neovation.repository.UserRepository;
import com.neovation.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    final private UserRepository userRepo;
    final private PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    public List<User> getAllUsers() {
        log.info("Admin fetching all users");
        return userRepo.findAll();
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        log.info("Admin attempting to update user with ID: {}", id);
        return userRepo.findById(id)
                .map(user -> {
                    user.setFirstName(updatedUser.getFirstName());
                    user.setLastName(updatedUser.getLastName());
                    user.setCompanyName(updatedUser.getCompanyName());
                    user.setEmail(updatedUser.getEmail());
                    user.setPhone(updatedUser.getPhone());
                    if (!updatedUser.getPassword().isBlank()) {
                        user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
                    }
                    user.setRole(updatedUser.getRole()); // Optional: only if changing role
                    User savedUser = userRepo.save(user);
                    log.info("Admin successfully updated user ID: {}", id);
                    return ResponseEntity.ok(savedUser);
                })
                .orElseGet(() -> {
                    log.warn("Admin failed to update - user not found with ID: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        log.info("Admin attempting to delete user with ID: {}", id);
        if (!userRepo.existsById(id)) {
            log.warn("Admin failed to delete - user not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }
        userRepo.deleteById(id);
        log.info("Admin successfully deleted user ID: {}", id);
        return ResponseEntity.ok("User deleted");
    }
}

