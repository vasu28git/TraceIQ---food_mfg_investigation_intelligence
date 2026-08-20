package com.investigation.platform.configuration.controller;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.configuration.dto.request.UpsertConfigurationRequest;
import com.investigation.platform.configuration.dto.response.ConfigurationResponse;
import com.investigation.platform.configuration.service.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configurations")
@RequiredArgsConstructor
@Tag(name = "Configurations", description = "Endpoints for tenant-specific configuration key-value pairs")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @PutMapping
    @Operation(summary = "Create or update tenant configuration")
    @PreAuthorize("hasAuthority('CONFIGURATION_UPDATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<ConfigurationResponse>> setConfiguration(
            @Valid @RequestBody UpsertConfigurationRequest request) {
        ConfigurationResponse response = configurationService.setConfiguration(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Configuration saved successfully"));
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get tenant configuration by key")
    @PreAuthorize("hasAuthority('CONFIGURATION_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<ConfigurationResponse>> getConfigurationByKey(@PathVariable("key") String key) {
        ConfigurationResponse response = configurationService.getConfigurationByKey(key);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Delete tenant configuration by key")
    @PreAuthorize("hasAuthority('CONFIGURATION_UPDATE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConfiguration(@PathVariable("key") String key) {
        configurationService.deleteConfiguration(key);
        return ResponseEntity.ok(ApiResponse.ok(null, "Configuration deleted successfully"));
    }

    @GetMapping
    @Operation(summary = "List all tenant configurations (paginated)")
    @PreAuthorize("hasAuthority('CONFIGURATION_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ConfigurationResponse>>> getAllConfigurations(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<ConfigurationResponse> response = configurationService.getAllConfigurations(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
