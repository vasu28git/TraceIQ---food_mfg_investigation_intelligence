package com.investigation.platform.platformadmin.controller;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.organization.dto.request.CreateOrganizationRequest;
import com.investigation.platform.organization.dto.request.UpdateOrganizationRequest;
import com.investigation.platform.organization.dto.response.OrganizationResponse;
import com.investigation.platform.platformadmin.dto.request.UpdateOrganizationStatusRequest;
import com.investigation.platform.platformadmin.service.PlatformAdminService;
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
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@Tag(name = "Platform Admin", description = "Platform-level APIs for managing organizations and system-wide operations (PLATFORM_ADMIN only)")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    @PostMapping("/organizations")
    @Operation(summary = "Platform Admin: Create a new organization and trigger onboarding bootstrap")
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse response = platformAdminService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "Organization created and bootstrap initiated"));
    }

    @GetMapping("/organizations")
    @Operation(summary = "Platform Admin: List all organizations across platform (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> getAllOrganizations(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<OrganizationResponse> response = platformAdminService.getAllOrganizations(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/organizations/{id}")
    @Operation(summary = "Platform Admin: Get any organization details by ID")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getOrganizationById(@PathVariable("id") UUID id) {
        OrganizationResponse response = platformAdminService.getOrganizationById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/organizations/{id}")
    @Operation(summary = "Platform Admin: Update organization details")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganization(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        OrganizationResponse response = platformAdminService.updateOrganization(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Organization updated successfully"));
    }

    @PatchMapping("/organizations/{id}/status")
    @Operation(summary = "Platform Admin: Change organization operational status (ACTIVE/INACTIVE/SUSPENDED)")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganizationStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        OrganizationResponse response = platformAdminService.updateOrganizationStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Organization status updated successfully"));
    }
}
