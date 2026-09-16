package com.taceiq.service;

import com.taceiq.dto.InvestigationConclusionRequest;
import com.taceiq.dto.InvestigationConclusionResponse;
import com.taceiq.entity.*;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InvestigationConclusionService {
    private static final Set<String> OUTCOMES = Set.of("CONFIRMED", "PARTIALLY_CONFIRMED", "NOT_CONFIRMED", "INCONCLUSIVE");
    private static final Set<String> CONFIDENCES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> LIFECYCLES = Set.of("DRAFT", "FINAL");

    private final InvestigationRepository investigationRepository;
    private final InvestigationFindingRepository findingRepository;
    private final InvestigationFindingEvidenceRepository findingEvidenceRepository;
    private final InvestigationConclusionRepository conclusionRepository;
    private final InvestigationConclusionFindingRepository conclusionFindingRepository;
    private final AuthorizationService authorizationService;

    public InvestigationConclusionService(InvestigationRepository investigationRepository,
                                           InvestigationFindingRepository findingRepository,
                                           InvestigationFindingEvidenceRepository findingEvidenceRepository,
                                           InvestigationConclusionRepository conclusionRepository,
                                           InvestigationConclusionFindingRepository conclusionFindingRepository,
                                           AuthorizationService authorizationService) {
        this.investigationRepository = investigationRepository;
        this.findingRepository = findingRepository;
        this.findingEvidenceRepository = findingEvidenceRepository;
        this.conclusionRepository = conclusionRepository;
        this.conclusionFindingRepository = conclusionFindingRepository;
        this.authorizationService = authorizationService;
    }

    private Long orgId() { return authorizationService.getCurrentOrgId(); }
    private void access() { authorizationService.requireAnyPermission("DECISION_ACCESS", "WORKSPACE_ACCESS", "INVESTIGATION_ACCESS"); }
    private Investigation investigation(Long id) {
        return investigationRepository.findByIdAndOrganisationOrgId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found"));
    }

    @Transactional(readOnly = true)
    public Optional<InvestigationConclusionResponse> get(Long investigationId) {
        access();
        investigation(investigationId);
        return conclusionRepository.findByOrganisationOrgIdAndInvestigationId(orgId(), investigationId).map(this::toResponse);
    }

    @Transactional
    public InvestigationConclusionResponse create(Long investigationId, InvestigationConclusionRequest request) {
        access();
        Investigation investigation = investigation(investigationId);
        if (conclusionRepository.findByOrganisationOrgIdAndInvestigationId(orgId(), investigationId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Investigation conclusion already exists");
        }
        validateRequest(request);
        InvestigationConclusion conclusion = InvestigationConclusion.builder()
                .organisation(investigation.getOrganisation()).investigation(investigation)
                .lifecycle(value(request.getLifecycle(), "DRAFT"))
                .outcome(value(request.getOutcome(), null)).confidence(normalizeNullable(request.getConfidence()))
                .summary(trimRequired(request.getSummary(), "Conclusion summary is required"))
                .investigatorReasoning(trimNullable(request.getInvestigatorReasoning()))
                .finalizedAt("FINAL".equals(value(request.getLifecycle(), "DRAFT")) ? Instant.now() : null)
                .createdBy(authorizationService.getCurrentUser()).updatedBy(authorizationService.getCurrentUser()).build();
        validateFinal(conclusion.getLifecycle(), investigationId, request);
        conclusion = conclusionRepository.save(conclusion);
        replaceFindings(conclusion, investigation, request);
        return toResponse(conclusion);
    }

    @Transactional
    public InvestigationConclusionResponse update(Long investigationId, InvestigationConclusionRequest request) {
        access();
        Investigation investigation = investigation(investigationId);
        validateRequest(request);
        InvestigationConclusion conclusion = conclusionRepository.findByOrganisationOrgIdAndInvestigationId(orgId(), investigationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation conclusion not found"));
        if ("FINAL".equals(conclusion.getLifecycle())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finalized investigation conclusions cannot be edited");
        }
        String lifecycle = value(request.getLifecycle(), "DRAFT");
        validateFinal(lifecycle, investigationId, request);
        conclusion.setLifecycle(lifecycle);
        conclusion.setOutcome(value(request.getOutcome(), null));
        conclusion.setConfidence(normalizeNullable(request.getConfidence()));
        conclusion.setSummary(trimRequired(request.getSummary(), "Conclusion summary is required"));
        conclusion.setInvestigatorReasoning(trimNullable(request.getInvestigatorReasoning()));
        conclusion.setUpdatedBy(authorizationService.getCurrentUser());
        if ("FINAL".equals(lifecycle)) conclusion.setFinalizedAt(Instant.now());
        conclusionRepository.save(conclusion);
        replaceFindings(conclusion, investigation, request);
        return toResponse(conclusion);
    }

    private void validateRequest(InvestigationConclusionRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conclusion request is required");
        String lifecycle = value(request.getLifecycle(), "DRAFT");
        if (!LIFECYCLES.contains(lifecycle)) invalid("lifecycle", LIFECYCLES);
        String outcome = value(request.getOutcome(), null);
        if (outcome == null || !OUTCOMES.contains(outcome)) invalid("outcome", OUTCOMES);
        String confidence = normalizeNullable(request.getConfidence());
        if (confidence != null && !CONFIDENCES.contains(confidence)) invalid("confidence", CONFIDENCES);
    }

    private void validateFinal(String lifecycle, Long investigationId, InvestigationConclusionRequest request) {
        if (!"FINAL".equals(lifecycle)) return;
        long findingCount = findingRepository.findByOrganisationOrgIdAndInvestigationIdOrderByUpdatedAtDesc(orgId(), investigationId).size();
        List<Long> selected = allIds(request);
        if (findingCount == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot finalize a conclusion because this investigation has no findings");
        if (selected.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Final conclusion requires at least one finding");
    }

    private void replaceFindings(InvestigationConclusion conclusion, Investigation investigation, InvestigationConclusionRequest request) {
        List<Long> supporting = cleanIds(request.getSupportingFindingIds());
        List<Long> contradicting = cleanIds(request.getContradictingFindingIds());
        Set<Long> all = new HashSet<>(supporting);
        if (contradicting.stream().anyMatch(all::contains)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A finding cannot both support and contradict a conclusion");
        conclusionFindingRepository.deleteByOrganisationOrgIdAndConclusionId(orgId(), conclusion.getId());
        for (Long id : supporting) saveLink(conclusion, investigation, id, "SUPPORTING");
        for (Long id : contradicting) saveLink(conclusion, investigation, id, "CONTRADICTING");
    }

    private void saveLink(InvestigationConclusion conclusion, Investigation investigation, Long findingId, String relation) {
        InvestigationFinding finding = findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(findingId, orgId(), investigation.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finding does not belong to this investigation: " + findingId));
        conclusionFindingRepository.save(InvestigationConclusionFinding.builder().organisation(investigation.getOrganisation())
                .investigation(investigation).conclusion(conclusion).finding(finding).relationshipType(relation)
                .createdBy(authorizationService.getCurrentUser()).build());
    }

    private InvestigationConclusionResponse toResponse(InvestigationConclusion conclusion) {
        List<InvestigationConclusionFinding> links = conclusionFindingRepository.findByOrganisationOrgIdAndConclusionIdOrderByIdAsc(orgId(), conclusion.getId());
        List<InvestigationConclusionResponse.LinkedFinding> supporting = links.stream().filter(link -> "SUPPORTING".equals(link.getRelationshipType())).map(this::toFinding).toList();
        List<InvestigationConclusionResponse.LinkedFinding> contradicting = links.stream().filter(link -> "CONTRADICTING".equals(link.getRelationshipType())).map(this::toFinding).toList();
        return InvestigationConclusionResponse.builder().id(conclusion.getId()).investigationId(conclusion.getInvestigation().getId())
                .lifecycle(conclusion.getLifecycle()).outcome(conclusion.getOutcome()).confidence(conclusion.getConfidence())
                .summary(conclusion.getSummary()).investigatorReasoning(conclusion.getInvestigatorReasoning())
                .createdByUserId(userId(conclusion.getCreatedBy())).updatedByUserId(userId(conclusion.getUpdatedBy()))
                .createdAt(conclusion.getCreatedAt()).updatedAt(conclusion.getUpdatedAt()).finalizedAt(conclusion.getFinalizedAt())
                .supportingFindings(supporting).contradictingFindings(contradicting).build();
    }

    private InvestigationConclusionResponse.LinkedFinding toFinding(InvestigationConclusionFinding link) {
        InvestigationFinding finding = link.getFinding();
        List<InvestigationConclusionResponse.LinkedEvidence> evidence = findingEvidenceRepository
                .findByOrganisationOrgIdAndInvestigationIdAndFindingId(orgId(), finding.getInvestigation().getId(), finding.getId()).stream()
                .map(item -> InvestigationConclusionResponse.LinkedEvidence.builder().stableId(item.getCanonicalEvidence().getExternalId())
                        .title(item.getCanonicalEvidence().getTitle()).sourceType(item.getCanonicalEvidence().getSourceType())
                        .relationshipType(item.getRelationshipType()).build()).toList();
        return InvestigationConclusionResponse.LinkedFinding.builder().id(finding.getId()).statement(finding.getTitle())
                .category(finding.getCategory()).confidence(finding.getConfidence()).status(finding.getStatus())
                .reasoning(finding.getDescription()).relationshipType(link.getRelationshipType()).evidence(evidence).build();
    }

    private static List<Long> allIds(InvestigationConclusionRequest request) {
        List<Long> result = new ArrayList<>(cleanIds(request.getSupportingFindingIds()));
        result.addAll(cleanIds(request.getContradictingFindingIds()));
        return result;
    }
    private static List<Long> cleanIds(List<Long> ids) { return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList(); }
    private static Long userId(User user) { return user == null ? null : user.getId(); }
    private static String value(String input, String fallback) { return input == null || input.isBlank() ? fallback : input.trim().toUpperCase(Locale.ROOT); }
    private static String normalizeNullable(String input) { return input == null || input.isBlank() ? null : input.trim().toUpperCase(Locale.ROOT); }
    private static String trimNullable(String input) { return input == null || input.isBlank() ? null : input.trim(); }
    private static String trimRequired(String input, String message) { String result = trimNullable(input); if (result == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); return result; }
    private static void invalid(String field, Set<String> allowed) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be one of " + allowed); }
}
