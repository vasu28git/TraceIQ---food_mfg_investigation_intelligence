package com.investigation.platform.user.controller;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.user.dto.request.CreateUserRequest;
import com.investigation.platform.user.dto.request.UpdateUserRequest;
import com.investigation.platform.user.dto.response.UserResponse;
import com.investigation.platform.user.enums.UserStatus;
import com.investigation.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Endpoints for user management within the tenant")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create user within current tenant organization")
    @PreAuthorize("hasAuthority('USER_CREATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "User created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID within current tenant organization")
    @PreAuthorize("hasAuthority('USER_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable("id") UUID id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user details within current tenant organization")
    @PreAuthorize("hasAuthority('USER_UPDATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "User updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user within current tenant organization")
    @PreAuthorize("hasAuthority('USER_DELETE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable("id") UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "User deleted successfully"));
    }

    @GetMapping
    @Operation(summary = "List users within current tenant organization (paginated)")
    @PreAuthorize("hasAuthority('USER_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(name = "status", required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<UserResponse> response;
        if (status != null) {
            response = userService.getUsersByStatus(status, pageable);
        } else {
            response = userService.getAllUsers(pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
