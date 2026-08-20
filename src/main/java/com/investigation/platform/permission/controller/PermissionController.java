package com.investigation.platform.permission.controller;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.permission.dto.response.PermissionResponse;
import com.investigation.platform.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Endpoints for system-defined permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @Operation(summary = "List all system permissions")
    @PreAuthorize("hasAuthority('PERMISSION_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> response = permissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/page")
    @Operation(summary = "List system permissions (paginated)")
    @PreAuthorize("hasAuthority('PERMISSION_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PermissionResponse>>> getPermissionsPage(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<PermissionResponse> response = permissionService.getPermissionsPage(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get permission by ID")
    @PreAuthorize("hasAuthority('PERMISSION_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(@PathVariable("id") UUID id) {
        PermissionResponse response = permissionService.getPermissionById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
