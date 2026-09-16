package com.taceiq.service;

import com.taceiq.dto.InvestigationEvidenceResponse;
import com.taceiq.dto.InvestigationFindingRequest;
import com.taceiq.dto.InvestigationFindingResponse;
import com.taceiq.entity.*;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class InvestigationFindingService {
    private final InvestigationRepository investigationRepository;
    private final InvestigationFindingRepository findingRepository;
    private final InvestigationFindingEvidenceRepository linkRepository;
    private final InvestigationEvidenceRepository investigationEvidenceRepository;
    private final CanonicalEvidenceRepository evidenceRepository;
    private final InvestigationEvidenceAssessmentRepository assessmentRepository;
    private final AuthorizationService authorizationService;

    @org.springframework.beans.factory.annotation.Autowired
    public InvestigationFindingService(InvestigationRepository investigationRepository,
                                       InvestigationFindingRepository findingRepository,
                                       InvestigationFindingEvidenceRepository linkRepository,
                                       InvestigationEvidenceRepository investigationEvidenceRepository,
                                       CanonicalEvidenceRepository evidenceRepository,
                                       InvestigationEvidenceAssessmentRepository assessmentRepository,
                                       AuthorizationService authorizationService) {
        this.investigationRepository = investigationRepository;
        this.findingRepository = findingRepository;
        this.linkRepository = linkRepository;
        this.investigationEvidenceRepository = investigationEvidenceRepository;
        this.evidenceRepository = evidenceRepository;
        this.assessmentRepository = assessmentRepository;
        this.authorizationService = authorizationService;
    }

    public InvestigationFindingService(InvestigationRepository investigationRepository,
                                       InvestigationFindingRepository findingRepository,
                                       InvestigationFindingEvidenceRepository linkRepository,
                                       CanonicalEvidenceRepository evidenceRepository,
                                       InvestigationEvidenceAssessmentRepository assessmentRepository,
                                       AuthorizationService authorizationService) {
        this(investigationRepository, findingRepository, linkRepository, null, evidenceRepository, assessmentRepository, authorizationService);
    }

    private Long orgId() { return authorizationService.getCurrentOrgId(); }
    private Investigation investigation(Long id) {
        return investigationRepository.findByIdAndOrganisationOrgId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found"));
    }
    private void access() { authorizationService.requireEvidenceGraphAccess(); }

    public List<InvestigationFindingResponse> list(Long investigationId) {
        access(); investigation(investigationId);
        return findingRepository.findByOrganisationOrgIdAndInvestigationIdOrderByUpdatedAtDesc(orgId(), investigationId)
                .stream().map(this::toResponse).toList();
    }

    public InvestigationFindingResponse get(Long investigationId, Long findingId) {
        access(); investigation(investigationId);
        return toResponse(findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(findingId, orgId(), investigationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found")));
    }

    @Transactional
    public InvestigationFindingResponse create(Long investigationId, InvestigationFindingRequest request) {
        access();
        Investigation investigation = investigation(investigationId);
        validateRequest(request);
        InvestigationFinding finding = InvestigationFinding.builder()
                .organisation(investigation.getOrganisation()).investigation(investigation)
                .title(request.getStatement().trim()).description(request.getReasoning())
                .category(normalize(request.getCategory())).confidence(normalize(request.getConfidence()))
                .status(defaultValue(request.getStatus(), "OPEN"))
                .createdBy(authorizationService.getCurrentUser()).build();
        finding = findingRepository.save(finding);
        replaceEvidence(finding, investigation, request.getEvidence());
        return toResponse(finding);
    }

    @Transactional
    public InvestigationFindingResponse update(Long investigationId, Long findingId, InvestigationFindingRequest request) {
        access(); Investigation investigation = investigation(investigationId); validateRequest(request);
        InvestigationFinding finding = findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(findingId, orgId(), investigationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));
        finding.setTitle(request.getStatement().trim()); finding.setDescription(request.getReasoning());
        finding.setCategory(normalize(request.getCategory())); finding.setConfidence(normalize(request.getConfidence()));
        finding.setStatus(defaultValue(request.getStatus(), "OPEN"));
        findingRepository.save(finding);
        replaceEvidence(finding, investigation, request.getEvidence());
        return toResponse(finding);
    }

    private void validateRequest(InvestigationFindingRequest request) {
        if (request == null || request.getStatement() == null || request.getStatement().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finding statement is required");
        validateOneOf("status", request.getStatus(), Set.of("OPEN", "CONFIRMED", "DISMISSED"));
        validateOneOfNullable("confidence", request.getConfidence(), Set.of("LOW", "MEDIUM", "HIGH"));
        validateOneOfNullable("category", request.getCategory(), Set.of("QUALITY", "PROCESS", "EQUIPMENT", "MATERIAL", "SUPPLIER", "OTHER"));
    }

    private void replaceEvidence(InvestigationFinding finding, Investigation investigation, List<InvestigationFindingRequest.EvidenceLinkRequest> requests) {
        linkRepository.deleteByOrganisationOrgIdAndInvestigationIdAndFindingId(orgId(), investigation.getId(), finding.getId());
        if (requests == null) return;
        Set<String> seen = new HashSet<>();
        for (var request : requests) {
            if (request == null || request.getStableId() == null || request.getStableId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence stableId is required");
            }
            String stableId = request.getStableId().trim();
            if (!seen.add(stableId)) continue;
            String relation = defaultValue(request.getRelationshipType(), "SUPPORTING").toUpperCase(Locale.ROOT);
            validateOneOf("relationshipType", relation, Set.of("SUPPORTING", "CONTRADICTING"));
            CanonicalEvidence evidence = evidenceRepository.findByExternalIdAndOrganisationOrgId(stableId, orgId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found: " + stableId));
            if (!eligible(investigation.getId(), evidence))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence must be reviewed with a supporting or contradictory assessment: " + stableId);
            linkRepository.save(InvestigationFindingEvidence.builder().organisation(investigation.getOrganisation())
                    .investigation(investigation).finding(finding).canonicalEvidence(evidence)
                    .relationshipType(relation).createdBy(authorizationService.getCurrentUser()).build());
        }
    }

    private boolean eligible(Long investigationId, CanonicalEvidence evidence) {
        if (Boolean.TRUE.equals(evidence.getIsDeleted())) return false;
        boolean inInvestigation = (evidence.getIncident() != null && investigationId.equals(evidence.getIncident().getId()))
            || (investigationEvidenceRepository != null
                && investigationEvidenceRepository.existsByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId(), investigationId, evidence.getId()));
        if (!inInvestigation) return false;
        var assessment = assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId(), investigationId, evidence.getId());
        if (assessment.isEmpty()) return false;
        InvestigationEvidenceAssessment savedAssessment = assessment.get();
        String assessmentValue = canonicalValue(savedAssessment.getAssessment());
        return "REVIEWED".equals(canonicalValue(savedAssessment.getReviewStatus()))
            && "RELEVANT".equals(canonicalValue(savedAssessment.getRelevance()))
            && assessmentValue != null
            && Set.of("SUPPORTS_INVESTIGATION", "CONTRADICTS_INVESTIGATION").contains(assessmentValue);
    }

    private InvestigationFindingResponse toResponse(InvestigationFinding finding) {
        List<InvestigationFindingResponse.LinkedEvidence> evidence = linkRepository
                .findByOrganisationOrgIdAndInvestigationIdAndFindingId(orgId(), finding.getInvestigation().getId(), finding.getId())
                .stream().map(link -> {
                    var assessment = assessmentRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(orgId(), finding.getInvestigation().getId(), link.getCanonicalEvidence().getId()).orElse(null);
                    return InvestigationFindingResponse.LinkedEvidence.builder().stableId(link.getCanonicalEvidence().getExternalId())
                            .sourceType(link.getCanonicalEvidence().getSourceType()).title(link.getCanonicalEvidence().getTitle())
                            .relationshipType(link.getRelationshipType()).reviewStatus(assessment == null ? null : assessment.getReviewStatus())
                            .relevance(assessment == null ? null : assessment.getRelevance()).assessment(assessment == null ? null : assessment.getAssessment()).build();
                }).toList();
        return InvestigationFindingResponse.builder().id(finding.getId()).investigationId(finding.getInvestigation().getId())
                .statement(finding.getTitle()).category(finding.getCategory()).confidence(finding.getConfidence()).status(finding.getStatus())
                .reasoning(finding.getDescription()).createdByUserId(finding.getCreatedBy() == null ? null : finding.getCreatedBy().getId())
                .createdAt(finding.getCreatedAt()).updatedAt(finding.getUpdatedAt()).evidence(evidence).build();
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT); }
    private static String canonicalValue(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static void validateOneOf(String field, String value, Set<String> allowed) { if (value != null && !allowed.contains(value.toUpperCase(Locale.ROOT))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be one of " + allowed); }
    private static void validateOneOfNullable(String field, String value, Set<String> allowed) { validateOneOf(field, value, allowed); }
}