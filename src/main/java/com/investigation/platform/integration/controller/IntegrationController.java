package com.investigation.platform.integration.controller;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.integration.dto.request.CreateIntegrationRequest;
import com.investigation.platform.integration.dto.request.UpdateIntegrationRequest;
import com.investigation.platform.integration.dto.response.IntegrationResponse;
import com.investigation.platform.integration.service.IntegrationService;
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
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "Endpoints for external system integrations within the tenant")
public class IntegrationController {

    private final IntegrationService integrationService;

    @PostMapping
    @Operation(summary = "Register integration within tenant")
    @PreAuthorize("hasAuthority('INTEGRATION_CREATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<IntegrationResponse>> createIntegration(
            @Valid @RequestBody CreateIntegrationRequest request) {
        IntegrationResponse response = integrationService.createIntegration(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "Integration registered successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get integration by ID within tenant")
    @PreAuthorize("hasAuthority('INTEGRATION_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<IntegrationResponse>> getIntegrationById(@PathVariable("id") UUID id) {
        IntegrationResponse response = integrationService.getIntegrationById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update integration within tenant")
    @PreAuthorize("hasAuthority('INTEGRATION_UPDATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<IntegrationResponse>> updateIntegration(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateIntegrationRequest request) {
        IntegrationResponse response = integrationService.updateIntegration(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Integration updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete integration within tenant")
    @PreAuthorize("hasAuthority('INTEGRATION_DELETE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteIntegration(@PathVariable("id") UUID id) {
        integrationService.deleteIntegration(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Integration deleted successfully"));
    }

    @GetMapping
    @Operation(summary = "List integrations within tenant (paginated)")
    @PreAuthorize("hasAuthority('INTEGRATION_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<IntegrationResponse>>> getAllIntegrations(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<IntegrationResponse> response = integrationService.getAllIntegrations(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "Trigger sync with external integration")
    @PreAuthorize("hasAuthority('INTEGRATION_UPDATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<IntegrationResponse>> triggerSync(@PathVariable("id") UUID id) {
        IntegrationResponse response = integrationService.triggerSync(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Integration sync initiated"));
    }
}
