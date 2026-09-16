package com.taceiq.controller;

import com.taceiq.dto.OrganisationProvisioningResult;
import com.taceiq.entity.Organisation;
import com.taceiq.service.OrganisationProvisioningService;
import com.taceiq.service.OrganisationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations")
@RequiredArgsConstructor
public class OrganisationController {

    private final OrganisationService organisationService;
    private final OrganisationProvisioningService provisioningService;

    // create org - orchestrated provisioning (org + ADMIN role + OG user)
    @PostMapping
    public ResponseEntity<OrganisationProvisioningResult> createOrg(@RequestBody Organisation organisation) {
        OrganisationProvisioningResult result = provisioningService.provisionOrganisation(organisation);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // get org
    @GetMapping("/{orgId}")
    public ResponseEntity<Organisation> getOrg(@PathVariable Long orgId) {
        Organisation org = organisationService.getOrg(orgId);
        return ResponseEntity.ok(org);
    }

    // get all org
    @GetMapping
    public ResponseEntity<List<Organisation>> getAllOrg() {
        List<Organisation> orgs = organisationService.getAllOrg();
        return ResponseEntity.ok(orgs);
    }

    // update org
    @PutMapping("/{orgId}")
    public ResponseEntity<Organisation> updateOrg(@PathVariable Long orgId, @RequestBody Organisation organisation) {
        Organisation updated = organisationService.updateOrg(orgId, organisation);
        return ResponseEntity.ok(updated);
    }

    // delete org
    @DeleteMapping("/{orgId}")
    public ResponseEntity<Void> deleteOrg(@PathVariable Long orgId) {
        organisationService.deleteOrg(orgId);
        return ResponseEntity.noContent().build();
    }
}
