package com.taceiq.graph;

import com.taceiq.graph.dto.GraphEvidencePageResponse;
import com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse;
import com.taceiq.graph.dto.TraceabilityDtos.IncidentTraceabilityResponse;
import com.taceiq.graph.service.GraphQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphQueryController {

    private final GraphQueryService queryService;

    @GetMapping("/evidence")
    public ResponseEntity<GraphEvidencePageResponse> searchEvidence(
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) Long incidentId,
            @RequestParam(required = false) Long excludeLinkedIncidentId,
            @RequestParam(required = false) String batchReference,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // incidentId is primary; batchReference is convenience alias for incident-first UX
        Long effectiveIncidentId = incidentId;
        if (effectiveIncidentId == null && batchReference != null && !batchReference.isBlank()) {
            // batchReference alone does not resolve to incident without org context – ignore for graph evidence (incident-scoped via /incidents/{id}/evidence is preferred)
            // Keep caseId/actorId path for org-wide evidence
        }
        GraphEvidencePageResponse resp = queryService.searchEvidence(caseId, actorId, effectiveIncidentId, page, size, excludeLinkedIncidentId);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/cases/{caseId}/traceability")
    public ResponseEntity<CaseTraceabilityResponse> traceCase(@PathVariable String caseId) {
        CaseTraceabilityResponse resp = queryService.traceCase(caseId);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/incidents/{incidentId}/traceability")
    public ResponseEntity<IncidentTraceabilityResponse> traceIncident(@PathVariable Long incidentId) {
        IncidentTraceabilityResponse resp = queryService.traceIncident(incidentId);
        return ResponseEntity.ok(resp);
    }
}
