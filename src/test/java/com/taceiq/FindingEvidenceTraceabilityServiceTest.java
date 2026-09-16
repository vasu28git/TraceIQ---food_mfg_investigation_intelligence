package com.taceiq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taceiq.entity.*;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.FindingEvidenceTraceabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FindingEvidenceTraceabilityServiceTest {
    @Mock InvestigationRepository investigationRepository;
    @Mock InvestigationFindingRepository findingRepository;
    @Mock InvestigationFindingEvidenceRepository findingEvidenceRepository;
    @Mock EvidenceCorrelationProvenanceRepository provenanceRepository;
    @Mock IngestedSourceRecordRepository sourceRecordRepository;
    @Mock AuthorizationService authorizationService;

    Investigation investigation;
    InvestigationFinding finding;
    CanonicalEvidence evidence;
    FindingEvidenceTraceabilityService service;

    @BeforeEach void setup() {
        MockitoAnnotations.openMocks(this);
        Organisation org = Organisation.builder().orgId(1L).name("Org").build();
        investigation = Investigation.builder().id(8L).organisation(org).investigationKey("INV-2025-001").batchReference("BATCH-1010").build();
        finding = InvestigationFinding.builder().id(2L).organisation(org).investigation(investigation).title("Maintenance finding").build();
        evidence = CanonicalEvidence.builder().id(5L).organisation(org).incident(investigation).externalId("SRC_CMMS_MAINT-1014").sourceType("CMMS").title("MAINT-1014").normalizedPayload("{\"sourceRecordId\":\"MAINT-1014\"}").build();
        service = new FindingEvidenceTraceabilityService(investigationRepository, findingRepository, findingEvidenceRepository, provenanceRepository, sourceRecordRepository, authorizationService, new ObjectMapper());
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        doNothing().when(authorizationService).requireAnyPermission(any(String[].class));
        when(investigationRepository.findByIdAndOrganisationOrgId(8L, 1L)).thenReturn(Optional.of(investigation));
        when(findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(2L, 1L, 8L)).thenReturn(Optional.of(finding));
        when(findingEvidenceRepository.findByOrganisationOrgIdAndInvestigationIdAndFindingId(1L, 8L, 2L)).thenReturn(List.of(link("SUPPORTING")));
        when(provenanceRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 5L)).thenReturn(List.of(provenance("DIRECT_BATCH_MATCH", "[\"Batch:BATCH-1010\",\"Machine:M-05\",\"CMMS Evidence:MAINT-1014\"]")));
        when(sourceRecordRepository.findByOrganisationOrgIdAndSourceTypeAndSourceRecordId(1L, "CMMS", "MAINT-1014")).thenReturn(Optional.of(sourceRecord()));
    }

    @Test void resolvesOneLinkedEvidencePathAndSourceRecord() {
        var result = service.trace(8L, 2L);
        assertEquals(1, result.getEvidence().size());
        var trace = result.getEvidence().get(0);
        assertEquals("SRC_CMMS_MAINT-1014", trace.getEvidenceId());
        assertEquals("MAINT-1014", trace.getSourceRecordId());
        assertEquals("PRIMARY", trace.getDiscoveryPaths().get(0).getClassification());
        assertEquals(List.of("Batch:BATCH-1010", "Machine:M-05", "CMMS Evidence:MAINT-1014"), trace.getDiscoveryPaths().get(0).getPath());
        assertEquals("MAINT-1014", trace.getSourceRecord().getSourceRecordId());
    }

    @Test void preservesMultipleValidPaths() {
        when(provenanceRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 5L))
                .thenReturn(List.of(provenance("MACHINE_MATCH_FROM_BATCH", "[\"Batch:BATCH-1010\",\"Machine:M-05\",\"CMMS Evidence:MAINT-1014\"]"), provenance("PRODUCT_MACHINE_MATCH", "[\"Batch:BATCH-1010\",\"Product:PRD-106\",\"Machine:M-05\",\"CMMS Evidence:MAINT-1014\"]")));
        var paths = service.trace(8L, 2L).getEvidence().get(0).getDiscoveryPaths();
        assertEquals(2, paths.size());
        assertEquals("PRIMARY", paths.get(0).getClassification());
        assertEquals("CROSS_BATCH_CONTEXT", paths.get(1).getClassification());
    }

    @Test void resolvesMultipleLinkedEvidenceRecords() {
        CanonicalEvidence second = CanonicalEvidence.builder().id(6L).organisation(evidence.getOrganisation()).incident(investigation)
                .externalId("SRC_LIMS_QA-2001").sourceType("LIMS").title("QA-2001").normalizedPayload("{\"sourceRecordId\":\"QA-2001\"}").build();
        InvestigationFindingEvidence secondLink = InvestigationFindingEvidence.builder().id(2L).organisation(investigation.getOrganisation()).investigation(investigation).finding(finding).canonicalEvidence(second).relationshipType("SUPPORTING").build();
        when(findingEvidenceRepository.findByOrganisationOrgIdAndInvestigationIdAndFindingId(1L, 8L, 2L)).thenReturn(List.of(link("SUPPORTING"), secondLink));
        when(provenanceRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 6L)).thenReturn(List.of(provenanceFor(second, "DIRECT_BATCH_MATCH", "[\"Batch:BATCH-1010\",\"LIMS Evidence:QA-2001\"]")));
        when(sourceRecordRepository.findByOrganisationOrgIdAndSourceTypeAndSourceRecordId(1L, "LIMS", "QA-2001")).thenReturn(Optional.empty());
        assertEquals(2, service.trace(8L, 2L).getEvidence().size());
    }

    @Test void missingPathDoesNotInferRelationship() {
        when(provenanceRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 8L, 5L)).thenReturn(List.of());
        var trace = service.trace(8L, 2L).getEvidence().get(0);
        assertTrue(trace.getDiscoveryPaths().isEmpty());
    }

    @Test void rejectsCrossInvestigationAccess() {
        when(findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(2L, 1L, 99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.trace(99L, 2L));
    }

    @Test void rejectsCrossTenantAccess() {
        when(investigationRepository.findByIdAndOrganisationOrgId(8L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.trace(8L, 2L));
    }

    @Test void rejectsUnauthorizedAccess() {
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireAnyPermission(any(String[].class));
        assertThrows(AccessDeniedException.class, () -> service.trace(8L, 2L));
    }

    private InvestigationFindingEvidence link(String relationship) { return InvestigationFindingEvidence.builder().id(1L).organisation(investigation.getOrganisation()).investigation(investigation).finding(finding).canonicalEvidence(evidence).relationshipType(relationship).build(); }
    private EvidenceCorrelationProvenance provenance(String reason, String path) { return EvidenceCorrelationProvenance.builder().id(1L).organisation(investigation.getOrganisation()).investigation(investigation).canonicalEvidence(evidence).reason(reason).sourceRecordId("MAINT-1014").connectionPath(path).discoveredAt(Instant.now()).build(); }
    private EvidenceCorrelationProvenance provenanceFor(CanonicalEvidence item, String reason, String path) { return EvidenceCorrelationProvenance.builder().id(2L).organisation(investigation.getOrganisation()).investigation(investigation).canonicalEvidence(item).reason(reason).sourceRecordId(item.getExternalId().substring(item.getExternalId().indexOf('_', 4) + 1)).connectionPath(path).discoveredAt(Instant.now()).build(); }
    private IngestedSourceRecord sourceRecord() { return IngestedSourceRecord.builder().id(50L).organisation(investigation.getOrganisation()).sourceType("CMMS").sourceRecordId("MAINT-1014").payload("{\"maintenanceStatus\":\"OVERDUE\"}").ingestedAt(Instant.now()).build(); }
}
