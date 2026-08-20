package com.investigation.platform.organization.controller;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.organization.dto.request.CreateOrganizationRequest;
import com.investigation.platform.organization.dto.request.UpdateOrganizationRequest;
import com.investigation.platform.organization.dto.response.OrganizationResponse;
import com.investigation.platform.organization.service.OrganizationService;
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
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Endpoints for managing SaaS organizations/tenants")
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @Operation(summary = "Create a new organization and bootstrap default roles")
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse response = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "Organization registered and bootstrapped successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization by ID")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getOrganizationById(@PathVariable("id") UUID id) {
        OrganizationResponse response = organizationService.getOrganizationById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update organization details")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganization(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        OrganizationResponse response = organizationService.updateOrganization(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Organization updated successfully"));
    }

    @GetMapping
    @Operation(summary = "List all organizations (paginated)")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> getAllOrganizations(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<OrganizationResponse> response = organizationService.getAllOrganizations(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete organization by ID")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteOrganization(@PathVariable("id") UUID id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Organization deleted successfully"));
    }
}
