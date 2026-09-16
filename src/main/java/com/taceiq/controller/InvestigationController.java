package com.taceiq.controller;

import com.taceiq.dto.CreateInvestigationRequest;
import com.taceiq.dto.InvestigationResponse;
import com.taceiq.service.InvestigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigations")
@RequiredArgsConstructor
public class InvestigationController {

    private final InvestigationService investigationService;

    @PostMapping
    public ResponseEntity<InvestigationResponse> create(@Valid @RequestBody CreateInvestigationRequest req) {
        InvestigationResponse resp = investigationService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public ResponseEntity<Page<InvestigationResponse>> list(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<InvestigationResponse> page = investigationService.listInvestigations(pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestigationResponse> get(@PathVariable Long id) {
        InvestigationResponse resp = investigationService.getInvestigation(id);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/evidence/discover")
    public ResponseEntity<com.taceiq.ingestion.EvidenceDiscoveryService.DiscoveryResult> discoverEvidence(@PathVariable Long id) {
        return ResponseEntity.ok(investigationService.discoverEvidence(id));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<InvestigationResponse> activate(@PathVariable Long id) {
        InvestigationResponse resp = investigationService.activate(id);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<InvestigationResponse> complete(@PathVariable Long id) {
        InvestigationResponse resp = investigationService.complete(id);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<InvestigationResponse> archive(@PathVariable Long id) {
        InvestigationResponse resp = investigationService.archive(id);
        return ResponseEntity.ok(resp);
    }
}

