package com.taceiq.graph.service;

import com.taceiq.entity.Integration;
import com.taceiq.entity.IntegrationSync;
import com.taceiq.graph.dto.GraphProjectionResult;
import com.taceiq.graph.dto.GraphReadinessResult;
import com.taceiq.graph.dto.GraphValidationResult;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.IntegrationSyncRepository;
import com.taceiq.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphReadinessService {

    private final IntegrationRepository integrationRepository;
    private final IntegrationSyncRepository syncRepository;
    private final CanonicalEvidenceRepository canonicalRepo;
    private final GraphProjectionService projectionService;
    private final GraphValidator validator;
    private final AuthorizationService authorizationService;

    public GraphReadinessResult checkReadiness(Long integrationId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireIntegrationRead();

        Integration integration = integrationRepository.findByIdAndOrganisationOrgId(integrationId, orgId)
                .orElseGet(() -> {
                    if (integrationRepository.findById(integrationId).isPresent()) {
                        throw new AccessDeniedException("Integration does not belong to your organisation");
                    }
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration not found with id: " + integrationId);
                });

        // A & B checked above
        var latestOpt = syncRepository.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(orgId, integrationId, "SUCCESS");
        if (latestOpt.isEmpty()) {
            return GraphReadinessResult.builder()
                    .organisationId(orgId).integrationId(integrationId).syncId(null)
                    .canonicalSyncStatus(null).graphProjected(false).graphValid(false).graphReady(false)
                    .evidenceCount(0).validationErrors(List.of("No successful canonical sync exists"))
                    .message("No successful canonical sync exists")
                    .build();
        }

        IntegrationSync sync = latestOpt.get();
        String canonicalStatus = sync.getStatus();
        // C already SUCCESS, but check
        if (!"SUCCESS".equals(canonicalStatus)) {
            return GraphReadinessResult.builder()
                    .organisationId(orgId).integrationId(integrationId).syncId(sync.getId())
                    .canonicalSyncStatus(canonicalStatus).graphProjected(false).graphValid(false).graphReady(false)
                    .evidenceCount(0).validationErrors(List.of("Latest sync not SUCCESS: " + canonicalStatus))
                    .message("Latest sync not SUCCESS")
                    .build();
        }

        // D: canonical evidence exists
        long canonicalCount = canonicalRepo.findByIntegrationIdAndOrganisationOrgId(integrationId, orgId).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .count();
        if (canonicalCount == 0) {
            return GraphReadinessResult.builder()
                    .organisationId(orgId).integrationId(integrationId).syncId(sync.getId())
                    .canonicalSyncStatus(canonicalStatus).graphProjected(false).graphValid(false).graphReady(false)
                    .evidenceCount(0).validationErrors(List.of("No canonical evidence (non-deleted) exists"))
                    .message("No canonical evidence")
                    .build();
        }

        // E: projection – scoped to org+integration, do NOT wrap PostgreSQL in Neo4j tx
        GraphProjectionResult projection;
        boolean projected = false;
        List<String> errors = new ArrayList<>();
        try {
            projection = projectionService.projectForIntegration(orgId, integrationId);
            projected = true;
        } catch (IllegalStateException e) {
            String safe = sanitize(e.getMessage());
            errors.add("Graph projection failed: " + safe);
            return GraphReadinessResult.builder()
                    .organisationId(orgId).integrationId(integrationId).syncId(sync.getId())
                    .canonicalSyncStatus(canonicalStatus).graphProjected(false).graphValid(false).graphReady(false)
                    .evidenceCount(canonicalCount).validationErrors(errors)
                    .message("Neo4j unavailable – canonical remains SUCCESS")
                    .build();
        } catch (Exception e) {
            String safe = sanitize(e.getMessage());
            errors.add("Graph projection failed: " + safe);
            return GraphReadinessResult.builder()
                    .organisationId(orgId).integrationId(integrationId).syncId(sync.getId())
                    .canonicalSyncStatus(canonicalStatus).graphProjected(false).graphValid(false).graphReady(false)
                    .evidenceCount(canonicalCount).validationErrors(errors)
                    .message("Projection failure – canonical preserved")
                    .build();
        }

        // F: validation
        GraphValidationResult validation;
        try {
            validation = validator.validate(orgId);
        } catch (Exception e) {
            String safe = sanitize(e.getMessage());
            errors.add("Graph validation failed: " + safe);
            return GraphReadinessResult.builder()
                    .organisationId(orgId).integrationId(integrationId).syncId(sync.getId())
                    .canonicalSyncStatus(canonicalStatus).graphProjected(projected).graphValid(false).graphReady(false)
                    .evidenceCount(canonicalCount).validationErrors(errors)
                    .message("Validation exception")
                    .build();
        }

        boolean graphValid = validation.isValid();
        long evidenceCount = validation.getEvidenceCount();
        if (validation.getErrors() != null) errors.addAll(validation.getErrors());

        // G: count matches
        boolean countMatches = evidenceCount == canonicalCount;
        if (!countMatches) {
            errors.add("Evidence count mismatch: canonical=" + canonicalCount + " graph=" + evidenceCount);
        }

        // H: evidenceCount >0
        boolean hasEvidence = evidenceCount > 0 && canonicalCount > 0;

        boolean graphReady = projected && graphValid && countMatches && hasEvidence && "SUCCESS".equals(canonicalStatus);

        String message;
        if (graphReady) message = "GRAPH_READY";
        else if (!graphValid) message = "Graph validation failed";
        else if (!countMatches) message = "Evidence count mismatch";
        else if (!hasEvidence) message = "Empty graph";
        else message = "Not ready";

        return GraphReadinessResult.builder()
                .organisationId(orgId).integrationId(integrationId).syncId(sync.getId())
                .canonicalSyncStatus(canonicalStatus)
                .graphProjected(projected)
                .graphValid(graphValid)
                .graphReady(graphReady)
                .evidenceCount(evidenceCount)
                .validationErrors(errors)
                .message(message)
                .build();
    }

    public boolean isOrgGraphReady(Long orgId) {
        // Org-level readiness: any integration for org that is GRAPH_READY OR manual evidence path
        var integrations = integrationRepository.findByOrganisationOrgId(orgId);
        for (Integration integ : integrations) {
            var opt = syncRepository.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(orgId, integ.getId(), "SUCCESS");
            if (opt.isEmpty()) continue;
            long canonicalCount = canonicalRepo.findByIntegrationIdAndOrganisationOrgId(integ.getId(), orgId).stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted())).count();
            if (canonicalCount == 0) continue;
            try {
                GraphValidationResult v = validator.validate(orgId);
                if (v.isValid() && v.getEvidenceCount() == canonicalCount && v.getEvidenceCount() > 0) return true;
            } catch (Exception ignored) {}
        }
        // Manual upload path: check for manual canonical evidence (integration_id IS NULL)
        // NOTE: This is the fast-path gate used on every write (Complaint/Investigation).
        // Do NOT trigger heavy Neo4j projection synchronously here – that caused 15s frontend timeouts
        // when org has 500+ evidences (each MERGE = round-trip to Aura). Projection is
        // best-effort async elsewhere (File upload post-commit, discovery). Just validate current graph state.
        try {
            long totalCanonical = canonicalRepo.findAllByOrganisationOrgId(orgId).stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                    .count();
            if (totalCanonical > 0) {
                try {
                    GraphValidationResult v = validator.validate(orgId);
                    if (v.isValid() && v.getEvidenceCount() > 0) return true;
                } catch (Exception e) {
                    log.debug("Manual validation failed for org {}: {}", orgId, e.getMessage());
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public GraphReadinessResult checkOrgReadiness(Long orgId) {
        var integrations = integrationRepository.findByOrganisationOrgId(orgId);
        IntegrationSync successfulSync = integrations.stream()
            .map(integ -> syncRepository.findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(orgId, integ.getId(), "SUCCESS"))
            .filter(Optional::isPresent)
            .map(Optional::get)
                .findFirst()
                .orElse(null);

        // Readiness is organisation-wide. Project and validate the complete tenant graph;
        // checking one integration against the org-wide Neo4j count makes multi-file orgs fail.
        try {
            long totalCanonical = canonicalRepo.findAllByOrganisationOrgId(orgId).stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                    .count();
            if (totalCanonical > 0) {
                try {
                    projectionService.projectForOrganisation(orgId);
                    var validation = validator.validate(orgId);
                    long graphCount = validation.getEvidenceCount();
                    boolean countMatches = graphCount == totalCanonical;
                    boolean hasEvidence = graphCount > 0;
                    boolean graphReady = validation.isValid() && countMatches && hasEvidence;
                    List<String> errors = new ArrayList<>();
                    if (validation.getErrors() != null) errors.addAll(validation.getErrors());
                    if (!countMatches) errors.add("Evidence count mismatch: canonical=" + totalCanonical + " graph=" + graphCount);
                    return GraphReadinessResult.builder()
                            .organisationId(orgId)
                            .integrationId(successfulSync != null && successfulSync.getIntegration() != null ? successfulSync.getIntegration().getId() : null)
                            .syncId(successfulSync != null ? successfulSync.getId() : null)
                            .canonicalSyncStatus(successfulSync != null ? successfulSync.getStatus() : null)
                            .graphProjected(true).graphValid(validation.isValid()).graphReady(graphReady)
                            .evidenceCount(graphCount).validationErrors(errors)
                            .message(graphReady ? "GRAPH_READY" : "Graph validation failed")
                            .build();
                } catch (Exception e) {
                    log.debug("Organisation graph readiness failed for org {}: {}", orgId, e.getMessage());
                    return GraphReadinessResult.builder()
                            .organisationId(orgId).graphReady(false).graphProjected(false).graphValid(false)
                            .evidenceCount(totalCanonical)
                            .validationErrors(List.of("Graph projection/validation failed: " + sanitize(e.getMessage())))
                            .message("Neo4j unavailable – canonical remains available")
                            .build();
                }
            }
        } catch (Exception ignored) {}
        return GraphReadinessResult.builder()
                .organisationId(orgId).graphReady(false).graphProjected(false).graphValid(false)
                .evidenceCount(0).validationErrors(List.of("No GRAPH_READY integration for organisation"))
                .message("Graph not ready")
                .build();
    }

    // Diagnostic helpers for InvestigationService logging - safe, no secrets
    public java.util.Map<String, Long> getCanonicalEvidenceCountsForDiagnostics(Long orgId) {
        long total = canonicalRepo.findAllByOrganisationOrgId(orgId).stream().filter(c -> !Boolean.TRUE.equals(c.getIsDeleted())).count();
        long manual = canonicalRepo.findAllByOrganisationOrgId(orgId).stream().filter(c -> c.getIntegration() == null && !Boolean.TRUE.equals(c.getIsDeleted())).count();
        long integration = total - manual;
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        map.put("total", total);
        map.put("manual", manual);
        map.put("integration", integration);
        return map;
    }

    public GraphValidationResult getGraphValidationForDiagnostics(Long orgId) {
        try {
            return validator.validate(orgId);
        } catch (Exception e) {
            return GraphValidationResult.builder().valid(false).evidenceCount(0).errors(List.of("validation failed: " + sanitize(e.getMessage()))).build();
        }
    }

    private String sanitize(String msg) {
        if (msg == null) return "Unknown error";
        String s = msg.replaceAll("(?i)Bearer\\s+\\S+", "Bearer ***")
                .replaceAll("(?i)api[_-]?key\\s*[:=]\\s*\\S+", "api_key=***")
                .replaceAll("(?i)password\\s*[:=]\\s*\\S+", "password=***");
        if (s.length() > 500) s = s.substring(0, 500);
        return s;
    }
}
