package com.taceiq.controller;

import com.taceiq.dto.InvestigationEvidenceResponse;
import com.taceiq.dto.InvestigationEvidenceAssessmentRequest;
import com.taceiq.dto.InvestigationEvidenceSummaryResponse;
import com.taceiq.dto.LinkEvidenceRequest;
import com.taceiq.service.InvestigationEvidenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigations/{investigationId}/evidence")
@RequiredArgsConstructor
public class InvestigationEvidenceController {

    private final InvestigationEvidenceService service;

    @PostMapping
    public ResponseEntity<InvestigationEvidenceResponse> link(@PathVariable Long investigationId,
                                                              @Valid @RequestBody LinkEvidenceRequest req) {
        InvestigationEvidenceResponse resp = service.linkEvidence(investigationId, req.getStableId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/{stableId}")
    public ResponseEntity<InvestigationEvidenceResponse> linkByPath(@PathVariable Long investigationId,
                                                                    @PathVariable String stableId) {
        InvestigationEvidenceResponse resp = service.linkEvidence(investigationId, stableId);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @DeleteMapping("/{stableId}")
    public ResponseEntity<Void> unlink(@PathVariable Long investigationId,
                                       @PathVariable String stableId) {
        service.unlinkEvidence(investigationId, stableId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<InvestigationEvidenceResponse>> list(
            @PathVariable Long investigationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort) {
        Page<InvestigationEvidenceResponse> resp = service.listEvidence(investigationId, page, size, search, sourceType, status, sort);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/summary")
    public ResponseEntity<InvestigationEvidenceSummaryResponse> summary(@PathVariable Long investigationId) {
        return ResponseEntity.ok(service.getEvidenceSummary(investigationId));
    }

    @GetMapping("/{stableId}")
    public ResponseEntity<InvestigationEvidenceResponse> detail(
            @PathVariable Long investigationId,
            @PathVariable String stableId) {
        return ResponseEntity.ok(service.getEvidenceDetail(investigationId, stableId));
    }

    @PutMapping("/{stableId}/assessment")
    public ResponseEntity<InvestigationEvidenceResponse> saveAssessment(
            @PathVariable Long investigationId,
            @PathVariable String stableId,
            @Valid @RequestBody InvestigationEvidenceAssessmentRequest request) {
        return ResponseEntity.ok(service.saveAssessment(investigationId, stableId, request));
    }
}
