package com.taceiq.graph;

import com.taceiq.graph.dto.GraphReadinessResult;
import com.taceiq.graph.service.GraphReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/{integrationId}/graph")
@RequiredArgsConstructor
public class GraphReadinessController {

    private final GraphReadinessService readinessService;

    @PostMapping("/ready")
    public ResponseEntity<GraphReadinessResult> ready(@PathVariable Long integrationId) {
        GraphReadinessResult result = readinessService.checkReadiness(integrationId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ready")
    public ResponseEntity<GraphReadinessResult> getReady(@PathVariable Long integrationId) {
        GraphReadinessResult result = readinessService.checkReadiness(integrationId);
        return ResponseEntity.ok(result);
    }
}
