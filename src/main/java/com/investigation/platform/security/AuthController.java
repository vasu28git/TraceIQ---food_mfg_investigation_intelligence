package com.investigation.platform.security;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.exception.UnauthorizedException;
import com.investigation.platform.security.dto.AuthResponse;
import com.investigation.platform.security.dto.LoginRequest;
import com.investigation.platform.security.dto.RefreshTokenRequest;
import com.investigation.platform.user.entity.User;
import com.investigation.platform.user.mapper.UserMapper;
import com.investigation.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication and token refresh")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().toLowerCase().trim(), request.getPassword())
        );

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        String accessToken = jwtService.generateToken(securityUser);
        String refreshToken = jwtService.generateRefreshToken(securityUser);

        User user = userRepository.findByUserIdAndOrgId(securityUser.getUserId(), securityUser.getOrgId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .orgId(securityUser.getOrgId())
                .user(userMapper.toResponse(user))
                .build();

        return ResponseEntity.ok(ApiResponse.ok(authResponse, "Authentication successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token with refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        if (!jwtService.isTokenValid(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        String email = jwtService.extractUsername(request.getRefreshToken());
        User user = userRepository.findByEmailWithRoleAndPermissions(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        SecurityUser securityUser = new SecurityUser(user);
        String newAccessToken = jwtService.generateToken(securityUser);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .orgId(securityUser.getOrgId())
                .user(userMapper.toResponse(user))
                .build();

        return ResponseEntity.ok(ApiResponse.ok(authResponse, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // Stateless JWT logout placeholder; can record audit log or blacklist token in Redis if needed
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }
}
