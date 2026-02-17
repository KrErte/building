package com.buildquote.controller;

import com.buildquote.dto.AuthRequest;
import com.buildquote.dto.AuthResponse;
import com.buildquote.entity.User;
import com.buildquote.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest.LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        var userOpt = userService.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            log.warn("Login failed: user not found for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vale e-post või parool"));
        }

        User user = userOpt.get();
        if (!userService.checkPassword(user, request.getPassword())) {
            log.warn("Login failed: invalid password for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vale e-post või parool"));
        }

        userService.updateLastLogin(user);

        // Generate a simple token (in production, use JWT)
        String token = UUID.randomUUID().toString();

        AuthResponse response = AuthResponse.builder()
                .user(AuthResponse.UserDto.fromEntity(user))
                .token(token)
                .message("Sisselogimine õnnestus")
                .build();

        log.info("Login successful for user: {}", user.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest.RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        try {
            User user = userService.createUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getCompany(),
                    request.getPhone(),
                    User.UserRole.USER,
                    User.SubscriptionPlan.FREE
            );

            // Generate a simple token (in production, use JWT)
            String token = UUID.randomUUID().toString();

            AuthResponse response = AuthResponse.builder()
                    .user(AuthResponse.UserDto.fromEntity(user))
                    .token(token)
                    .message("Konto loodud edukalt")
                    .build();

            log.info("Registration successful for user: {}", user.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "See e-posti aadress on juba kasutusel"));
        } catch (Exception e) {
            log.error("Registration error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registreerimine ebaõnnestus"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        // In a real app, validate the token and return user info
        // For now, this is a placeholder
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Not authenticated"));
    }
}
