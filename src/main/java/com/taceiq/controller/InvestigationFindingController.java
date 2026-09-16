package com.taceiq.controller;

import com.taceiq.dto.InvestigationFindingRequest;
import com.taceiq.dto.InvestigationFindingResponse;
import com.taceiq.dto.FindingEvidenceTraceabilityResponse;
import com.taceiq.service.InvestigationFindingService;
import com.taceiq.service.FindingEvidenceTraceabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/investigations/{investigationId}/findings")
@RequiredArgsConstructor
public class InvestigationFindingController {
    private final InvestigationFindingService service;
    private final FindingEvidenceTraceabilityService traceabilityService;
    @GetMapping public ResponseEntity<List<InvestigationFindingResponse>> list(@PathVariable Long investigationId) { return ResponseEntity.ok(service.list(investigationId)); }
    @GetMapping("/{findingId}") public ResponseEntity<InvestigationFindingResponse> get(@PathVariable Long investigationId, @PathVariable Long findingId) { return ResponseEntity.ok(service.get(investigationId, findingId)); }
    @PostMapping public ResponseEntity<InvestigationFindingResponse> create(@PathVariable Long investigationId, @Valid @RequestBody InvestigationFindingRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(investigationId, request)); }
    @PutMapping("/{findingId}") public ResponseEntity<InvestigationFindingResponse> update(@PathVariable Long investigationId, @PathVariable Long findingId, @Valid @RequestBody InvestigationFindingRequest request) { return ResponseEntity.ok(service.update(investigationId, findingId, request)); }
    @GetMapping("/{findingId}/traceability") public ResponseEntity<FindingEvidenceTraceabilityResponse> traceability(@PathVariable Long investigationId, @PathVariable Long findingId) { return ResponseEntity.ok(traceabilityService.trace(investigationId, findingId)); }
}