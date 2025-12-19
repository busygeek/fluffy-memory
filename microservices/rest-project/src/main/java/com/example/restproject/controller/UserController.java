package com.example.restproject.controller;

import com.example.restproject.dto.UserDTO;
import com.example.restproject.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "BearerAuth")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get all users")
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        logger.info("Received GET request for all users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Get user by ID")
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        logger.info("Received GET request for user with id: {}", id);
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create a new user")
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody UserDTO userDTO) {
        logger.info("Received POST request to create user: {}", userDTO.name());
        return new ResponseEntity<>(userService.createUser(userDTO), HttpStatus.CREATED);
    }

    @Operation(summary = "Update an existing user")
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @Valid @RequestBody UserDTO userDTO) {
        logger.info("Received PUT request to update user with id: {}", id);
        return userService.updateUser(id, userDTO)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Partially update an existing user")
    @PatchMapping("/{id}")
    public ResponseEntity<UserDTO> patchUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        logger.info("Received PATCH request to update user with id: {}", id);
        return userService.patchUser(id, userDTO)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a user")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        logger.info("Received DELETE request for user with id: {}", id);
        if (userService.deleteUser(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(summary = "Login to get JWT token")
    @PostMapping("/auth/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest loginRequest) {
        logger.info("Received login request for user: {}", loginRequest.username());
        // Simplified login for demo purposes
        if ("admin".equals(loginRequest.username()) && "password".equals(loginRequest.password())) {
            return ResponseEntity.ok("Bearer " + new com.example.restproject.security.JwtUtil().generateToken(loginRequest.username()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    record LoginRequest(String username, String password) {}
}
