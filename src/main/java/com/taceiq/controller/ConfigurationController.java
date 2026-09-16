package com.taceiq.controller;

import com.taceiq.entity.Configuration;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/configurations")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationService configurationService;
    private final AuthorizationService authorizationService;
    private final OrganisationRepository organisationRepository;

    private void assertSameOrganisation(Configuration config) {
        Long currentOrgId = authorizationService.getCurrentOrgId();
        Long configOrgId = config.getOrganisation() != null ? config.getOrganisation().getOrgId() : null;
        if (configOrgId == null || !configOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Configuration does not belong to your organisation");
        }
    }

    @PostMapping
    public ResponseEntity<Configuration> createConfiguration(@RequestBody Configuration config) {
        // Order: Authentication -> org isolation (derive org) -> required permission -> validation -> operation
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireConfigCreate();
        // Do not accept organisation from client - derive from security context
        config.setOrganisation(organisationRepository.getReferenceById(orgId));
        Configuration created = configurationService.createConfiguration(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Configuration> getConfigurationById(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireConfigRead();
        Configuration config = configurationService.getConfigurationById(id, orgId);
        return ResponseEntity.ok(config);
    }

    @GetMapping("/by-key")
    public ResponseEntity<Configuration> getConfigurationByKey(@RequestParam String key) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireConfigRead();
        Configuration config = configurationService.getConfigurationByKey(key, orgId);
        return ResponseEntity.ok(config);
    }

    @GetMapping
    public ResponseEntity<List<Configuration>> getConfigurationsByOrganisation() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireConfigRead();
        List<Configuration> configs = configurationService.getConfigurationsByOrganisation(orgId);
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/definitions")
    public ResponseEntity<List<com.taceiq.entity.ConfigurationDefinition>> getDefinitions() {
        // Global catalog – any authenticated org user with read permission can view
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireConfigRead();
        List<com.taceiq.entity.ConfigurationDefinition> defs = configurationService.getAllDefinitions();
        return ResponseEntity.ok(defs);
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<Configuration> updateConfiguration(@PathVariable Long id, @RequestBody Configuration config) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireConfigUpdate();
        Configuration updated = configurationService.updateConfiguration(id, config, orgId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> deleteConfiguration(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireConfigDelete();
        configurationService.deleteConfiguration(id, orgId);
        return ResponseEntity.noContent().build();
    }
}
