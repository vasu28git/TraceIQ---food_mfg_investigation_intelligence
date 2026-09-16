package com.taceiq.graph;

import com.taceiq.graph.dto.GraphProjectionResult;
import com.taceiq.graph.dto.GraphValidationResult;
import com.taceiq.graph.service.GraphProjectionService;
import com.taceiq.graph.service.GraphValidator;
import com.taceiq.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphProjectionService projectionService;
    private final GraphValidator validator;
    private final AuthorizationService authorizationService;

    @PostMapping("/projection")
    public ResponseEntity<GraphProjectionResult> project() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        GraphProjectionResult result = projectionService.projectForOrganisation(orgId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/integrations/{integrationId}/projection")
    public ResponseEntity<GraphProjectionResult> projectForIntegration(@PathVariable Long integrationId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        GraphProjectionResult result = projectionService.projectForIntegration(orgId, integrationId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/rebuild")
    public ResponseEntity<GraphProjectionResult> rebuild() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        GraphProjectionResult result = projectionService.rebuildForOrganisation(orgId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/validation")
    public ResponseEntity<GraphValidationResult> validate() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();
        GraphValidationResult result = validator.validate(orgId);
        return ResponseEntity.ok(result);
    }
}
