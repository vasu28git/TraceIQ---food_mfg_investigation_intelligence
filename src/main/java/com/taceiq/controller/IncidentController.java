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

/**
 * Incident alias for investigation — domain terminology.
 * Investigation remains persistence entity; Incident is user-facing alias.
 * All operations delegate to InvestigationService to preserve tenant isolation and lifecycle.
 */
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final InvestigationService investigationService;

    @PostMapping
    public ResponseEntity<InvestigationResponse> create(@Valid @RequestBody CreateInvestigationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(investigationService.create(req));
    }

    @GetMapping
    public ResponseEntity<Page<InvestigationResponse>> list(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(investigationService.listInvestigations(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestigationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(investigationService.getInvestigation(id));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<InvestigationResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(investigationService.activate(id));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<InvestigationResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(investigationService.complete(id));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<InvestigationResponse> archive(@PathVariable Long id) {
        return ResponseEntity.ok(investigationService.archive(id));
    }
}
