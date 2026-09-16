package com.taceiq.controller;

import com.taceiq.dto.SyncResponse;
import com.taceiq.entity.Integration;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.IntegrationService;
import com.taceiq.service.IntegrationSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;
    private final IntegrationSyncService syncService;
    private final AuthorizationService authorizationService;

    @PostMapping
    public ResponseEntity<Integration> createIntegration(@RequestBody Integration integration) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationCreate();
        Integration created = integrationService.createIntegration(integration, orgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Integration> getIntegrationById(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        Integration integration = integrationService.getIntegrationById(id, orgId);
        return ResponseEntity.ok(integration);
    }

    @GetMapping("/by-name")
    public ResponseEntity<Integration> getIntegrationByName(@RequestParam String name) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        Integration integration = integrationService.getIntegrationByName(name, orgId);
        return ResponseEntity.ok(integration);
    }

    @GetMapping
    public ResponseEntity<List<Integration>> getIntegrationsByOrganisation() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        List<Integration> integrations = integrationService.getIntegrationsByOrganisation(orgId);
        return ResponseEntity.ok(integrations);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Integration> updateIntegration(@PathVariable Long id, @RequestBody Integration integration) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationUpdate();
        Integration updated = integrationService.updateIntegration(id, orgId, integration);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Integration> updateIntegrationStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationUpdate();
        String status = body.get("status");
        Integration updated = integrationService.updateIntegrationStatus(id, orgId, status);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, String>> testConnection(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        String result = integrationService.testConnection(id, orgId);
        return ResponseEntity.ok(Map.of("message", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationDelete();
        integrationService.deleteIntegration(id, orgId);
        return ResponseEntity.noContent().build();
    }

    // Legacy single-arg path for tests that call ctrl.createSync(id) directly (without incidentId)
    public ResponseEntity<SyncResponse> createSync(Long id) {
        try {
            SyncResponse resp = syncService.syncIntegration(id);
            if ("SUCCESS".equals(resp.getStatus())) {
                return ResponseEntity.ok(resp);
            } else if ("FAILED".equals(resp.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(resp);
            } else {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<SyncResponse> createSync(@PathVariable Long id,
                                                   @RequestParam(value = "incidentId", required = false) Long incidentId) {
        try {
            SyncResponse resp = syncService.syncIntegration(id, incidentId);

            // Return 200 on success, 202 if still pending (should not happen with syncIntegration)
            if ("SUCCESS".equals(resp.getStatus())) {
                return ResponseEntity.ok(resp);
            } else if ("FAILED".equals(resp.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(resp);
            } else {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // For fetch failures (e.g., EvidenceApiClient not configured, network), return 502
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }
}
