package com.taceiq.security;

import com.taceiq.entity.User;
import com.taceiq.repository.UserRepository;
import com.taceiq.security.dto.ChangePasswordRequest;
import com.taceiq.security.dto.LoginRequest;
import com.taceiq.security.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Value("${app.platform-admin.email}")
    private String adminEmail;

    @Value("${app.platform-admin.password}")
    private String adminPassword;

    public LoginResponse login(LoginRequest request) {
        String identifier = resolveIdentifier(request);
        String rawPassword = request.getPassword();

        if (identifier == null || identifier.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // 1. Try Platform Admin (email/password via config)
        if (adminEmail.equalsIgnoreCase(identifier)) {
            boolean passwordMatches;
            if (isEncoded(adminPassword)) {
                passwordMatches = passwordEncoder.matches(rawPassword, adminPassword);
            } else {
                passwordMatches = adminPassword.equals(rawPassword);
            }
            if (passwordMatches) {
                String token = jwtService.generateToken(adminEmail);
                return LoginResponse.builder()
                        .accessToken(token)
                        .tokenType("Bearer")
                        .expiresIn(jwtService.getExpirationMs())
                        .email(adminEmail)
                        .build();
            }
            // if email matches admin but password fails, fall through to BadCredentials
            throw new BadCredentialsException("Invalid credentials");
        }

        // 2. Try Organisation User via UserRepository
        // identifier is username (LoginRequest.username or email field used as username)
        Optional<User> userOpt = userRepository.findByUsername(identifier);
        // fallback: if identifier was supplied via email field and not found, try username field
        if (userOpt.isEmpty() && request.getUsername() != null && request.getEmail() != null
                && !request.getUsername().equals(request.getEmail())) {
            userOpt = userRepository.findByUsername(request.getUsername());
        }

        User user = userOpt.orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // verify BCrypt password
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // check status - only ACTIVE allowed
        if (user.getStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BadCredentialsException("User not active: " + user.getStatus());
        }

        // get Organisation + Role are already via user.getOrganisation() / user.getRole() (lazy, but within transaction if needed)
        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .email(user.getUsername())
                .build();
    }

    private String resolveIdentifier(LoginRequest request) {
        // username takes precedence, else email
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            return request.getUsername().trim();
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return request.getEmail().trim();
        }
        return null;
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        if (request.getCurrentPassword() == null || request.getNewPassword() == null || request.getConfirmPassword() == null) {
            throw new IllegalArgumentException("All password fields are required");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }
        if (request.getNewPassword().isBlank() || request.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }
        // Platform Admin password is env-driven, not DB - not changeable via this endpoint
        if (adminEmail.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Platform Admin password cannot be changed via this endpoint");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // current password correct?
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        // hash new password -> save -> mustChangePassword = false
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private boolean isEncoded(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }
}
