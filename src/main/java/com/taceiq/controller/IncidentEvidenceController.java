package com.taceiq.controller;

import com.taceiq.dto.InvestigationEvidenceResponse;
import com.taceiq.dto.BatchConnectionMapResponse;
import com.taceiq.dto.LinkEvidenceRequest;
import com.taceiq.service.InvestigationEvidenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Incident terminology alias for investigation evidence.
 * Investigation remains persistence; Incident is domain alias.
 * Delegates to same service to preserve tenant isolation and behavior.
 */
@RestController
@RequestMapping("/api/incidents/{incidentId}/evidence")
@RequiredArgsConstructor
public class IncidentEvidenceController {

    private final InvestigationEvidenceService service;

    @PostMapping
    public ResponseEntity<InvestigationEvidenceResponse> link(@PathVariable Long incidentId,
                                                              @Valid @RequestBody LinkEvidenceRequest req) {
        InvestigationEvidenceResponse resp = service.linkEvidence(incidentId, req.getStableId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/{stableId}")
    public ResponseEntity<InvestigationEvidenceResponse> linkByPath(@PathVariable Long incidentId,
                                                                    @PathVariable String stableId) {
        InvestigationEvidenceResponse resp = service.linkEvidence(incidentId, stableId);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @DeleteMapping("/{stableId}")
    public ResponseEntity<Void> unlink(@PathVariable Long incidentId,
                                       @PathVariable String stableId) {
        service.unlinkEvidence(incidentId, stableId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<InvestigationEvidenceResponse>> list(
            @PathVariable Long incidentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort) {
        Page<InvestigationEvidenceResponse> resp = service.listEvidence(incidentId, page, size, search, sourceType, status, sort);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/connection-map")
    public ResponseEntity<BatchConnectionMapResponse> connectionMap(@PathVariable Long incidentId) {
        return ResponseEntity.ok(service.getBatchConnectionMap(incidentId));
    }

    @GetMapping("/{stableId}")
    public ResponseEntity<InvestigationEvidenceResponse> detail(
            @PathVariable Long incidentId,
            @PathVariable String stableId) {
        return ResponseEntity.ok(service.getEvidenceDetail(incidentId, stableId));
    }
}
