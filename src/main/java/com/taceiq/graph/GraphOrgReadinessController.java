package com.taceiq.graph;

import com.taceiq.graph.dto.GraphReadinessResult;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphOrgReadinessController {

    private final GraphReadinessService readinessService;
    private final AuthorizationService authorizationService;

    @GetMapping("/ready")
    public ResponseEntity<GraphReadinessResult> getOrgGraphReady() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        GraphReadinessResult result = readinessService.checkOrgReadiness(orgId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ready")
    public ResponseEntity<GraphReadinessResult> postOrgGraphReady() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        GraphReadinessResult result = readinessService.checkOrgReadiness(orgId);
        return ResponseEntity.ok(result);
    }
}
