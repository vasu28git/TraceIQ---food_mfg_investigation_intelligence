package com.taceiq.controller;

import com.taceiq.dto.InvestigationConclusionRequest;
import com.taceiq.dto.InvestigationConclusionResponse;
import com.taceiq.service.InvestigationConclusionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigations/{investigationId}/conclusion")
@RequiredArgsConstructor
public class InvestigationConclusionController {
    private final InvestigationConclusionService service;

    @GetMapping
    public ResponseEntity<InvestigationConclusionResponse> get(@PathVariable Long investigationId) {
        return service.get(investigationId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<InvestigationConclusionResponse> create(@PathVariable Long investigationId, @Valid @RequestBody InvestigationConclusionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(investigationId, request));
    }

    @PutMapping
    public ResponseEntity<InvestigationConclusionResponse> update(@PathVariable Long investigationId, @Valid @RequestBody InvestigationConclusionRequest request) {
        return ResponseEntity.ok(service.update(investigationId, request));
    }
}
