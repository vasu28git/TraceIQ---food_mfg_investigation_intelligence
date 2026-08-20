package com.investigation.platform.role.controller;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.role.dto.request.AssignPermissionsRequest;
import com.investigation.platform.role.dto.request.CreateRoleRequest;
import com.investigation.platform.role.dto.request.UpdateRoleRequest;
import com.investigation.platform.role.dto.response.RoleResponse;
import com.investigation.platform.role.service.RoleService;
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
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Endpoints for role management within the tenant")
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @Operation(summary = "Create custom role within tenant")
    @PreAuthorize("hasAuthority('ROLE_CREATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "Role created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID within tenant")
    @PreAuthorize("hasAuthority('ROLE_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable("id") UUID id) {
        RoleResponse response = roleService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update role details within tenant")
    @PreAuthorize("hasAuthority('ROLE_UPDATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse response = roleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Role updated successfully"));
    }

    @PutMapping("/{roleId}/permissions")
    @Operation(summary = "Assign permissions to a role within tenant")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissions(
            @PathVariable("roleId") UUID roleId,
            @Valid @RequestBody AssignPermissionsRequest request) {
        RoleResponse response = roleService.assignPermissions(roleId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Permissions assigned to role successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete role within tenant")
    @PreAuthorize("hasAuthority('ROLE_DELETE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable("id") UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Role deleted successfully"));
    }

    @GetMapping
    @Operation(summary = "List roles within tenant (paginated)")
    @PreAuthorize("hasAuthority('ROLE_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> getAllRoles(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<RoleResponse> response = roleService.getAllRoles(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
