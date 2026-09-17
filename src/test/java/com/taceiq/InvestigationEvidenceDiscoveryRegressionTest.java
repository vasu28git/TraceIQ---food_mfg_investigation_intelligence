package com.taceiq;

import com.taceiq.dto.EvidenceDiscoveryResult;
import com.taceiq.dto.InvestigationEvidenceResponse;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.InvestigationEvidence;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.User;
import com.taceiq.graph.GraphQueryRepository;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.InvestigationEvidenceAssessmentRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.EvidenceRelevanceClassifier;
import com.taceiq.service.InvestigationEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused regression tests for Investigation Evidence Discovery:
 * A. BATCH-1010 -> QA-2001           -> distance=1, direct route (DIRECT_BATCH)
 * B. BATCH-1010 -> M-05 -> MAINT-1014 -> distance=2, route=MACHINE_ROUTE, relevance=RELATED
 * C. BATCH-1010 -> WZ-A1 -> LOG-3003  -> distance=2, route=WAREHOUSE_ROUTE
 * D. BATCH-1010 -> WZ-A1 -> BATCH-1031 -> must NOT produce any InvestigationEvidence row
 * E. Traversal through a Customer     -> CUSTOMER_ROUTE, not MACHINE_ROUTE
 * F. Rediscovery over an already-reviewed row -> review state preserved, no duplicate row
 * G. Multi-tenant isolation: Discovery for org A must not return evidence reachable only through org B
 */
public class InvestigationEvidenceDiscoveryRegressionTest {

    @Mock private InvestigationRepository investigationRepository;
    @Mock private CanonicalEvidenceRepository canonicalRepo;
    @Mock private InvestigationEvidenceRepository linkRepository;
    @Mock private AuthorizationService authService;
    @Mock private GraphReadinessService graphReadinessService;
    @Mock private InvestigationEvidenceAssessmentRepository assessmentRepository;

    private InvestigationEvidenceService evidenceService;
    private EvidenceRelevanceClassifier classifier;

    private final Organisation orgA = Organisation.builder().orgId(1L).name("OrgA").build();
    private final Organisation orgB = Organisation.builder().orgId(2L).name("OrgB").build();
    private final User userA = User.builder().id(101L).username("userA").organisation(orgA).build();
    private final Investigation invA = Investigation.builder()
            .id(10L)
            .organisation(orgA)
            .investigationKey("INV-A")
            .batchReference("BATCH-1010")
            .status("ACTIVE")
            .createdBy(userA)
            .build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        classifier = new EvidenceRelevanceClassifier();
        evidenceService = new InvestigationEvidenceService(
                investigationRepository,
                canonicalRepo,
                linkRepository,
                authService,
                graphReadinessService,
                null,
                null,
                assessmentRepository
        );
        evidenceService.setRelevanceClassifier(classifier);

        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authService.getCurrentUser()).thenReturn(userA);
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(invA));
        lenient().when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(10L), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
    }

    // A. BATCH-1010 -> QA-2001 -> evidenceStableId=QA-2001, distance=1, direct route
    @Test
    void testA_DirectBatchEvidence_QA2001() {
        CanonicalEvidence ceQa = CanonicalEvidence.builder()
                .id(201L)
                .organisation(orgA)
                .externalId("QA-2001")
                .title("Seal Strength Test QA-2001")
                .sourceType("LIMS")
                .normalizedPayload("{\"batchReference\":\"BATCH-1010\",\"sourceRecordId\":\"QA-2001\"}")
                .build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("QA-2001", 1L)).thenReturn(Optional.of(ceQa));

        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("QA-2001")
                .distance(1)
                .semanticRoute("DIRECT_BATCH")
                .sourceType("LIMS")
                .title("Seal Strength Test QA-2001")
                .nodePath(List.of("BATCH-1010", "QA-2001"))
                .labelPath(List.of("Batch", "Evidence"))
                .relationshipPath(List.of("REFERENCES"))
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("DIRECT", classification.relevance());

        int attached = evidenceService.attachDiscoveredEvidence(1L, 10L, "BATCH-1010", List.of(discovery));
        assertEquals(1, attached);

        ArgumentCaptor<InvestigationEvidence> captor = ArgumentCaptor.forClass(InvestigationEvidence.class);
        verify(linkRepository, atLeastOnce()).save(captor.capture());
        InvestigationEvidence saved = captor.getValue();

        assertEquals(1, saved.getDistance());
        assertEquals("DIRECT", saved.getRelevance());
        assertEquals("PENDING_REVIEW", saved.getReviewStatus());
    }

    // B. BATCH-1010 -> M-05 -> MAINT-1014 -> evidenceStableId=MAINT-1014, distance=2, route=MACHINE_ROUTE, relevance=RELATED
    @Test
    void testB_MachineRouteEvidence_MAINT1014() {
        CanonicalEvidence ceMaint = CanonicalEvidence.builder()
                .id(202L)
                .organisation(orgA)
                .externalId("MAINT-1014")
                .title("Calibration for M-05")
                .sourceType("CMMS")
                .normalizedPayload("{\"machineReference\":\"M-05\",\"sourceRecordId\":\"MAINT-1014\"}")
                .build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("MAINT-1014", 1L)).thenReturn(Optional.of(ceMaint));

        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("MAINT-1014")
                .distance(2)
                .semanticRoute("MACHINE_ROUTE")
                .sourceType("CMMS")
                .title("Calibration for M-05")
                .nodePath(List.of("BATCH-1010", "M-05", "MAINT-1014"))
                .labelPath(List.of("Batch", "Machine", "Evidence"))
                .relationshipPath(List.of("ASSOCIATED_WITH", "REFERENCES"))
                .intermediateEntityId("M-05")
                .intermediateEntityType("Machine")
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("RELATED", classification.relevance());
        assertTrue(classification.discoveryReason().toLowerCase().contains("machine"));

        int attached = evidenceService.attachDiscoveredEvidence(1L, 10L, "BATCH-1010", List.of(discovery));
        assertEquals(1, attached);

        ArgumentCaptor<InvestigationEvidence> captor = ArgumentCaptor.forClass(InvestigationEvidence.class);
        verify(linkRepository, atLeastOnce()).save(captor.capture());
        InvestigationEvidence saved = captor.getValue();

        assertEquals(2, saved.getDistance());
        assertEquals("RELATED", saved.getRelevance());
        assertTrue(saved.getDiscoveryReason().contains("M-05"));
    }

    // C. BATCH-1010 -> WZ-A1 -> LOG-3003 -> evidenceStableId=LOG-3003, distance=2, route=WAREHOUSE_ROUTE
    @Test
    void testC_WarehouseRouteEvidence_LOG3003() {
        CanonicalEvidence ceLog = CanonicalEvidence.builder()
                .id(203L)
                .organisation(orgA)
                .externalId("LOG-3003")
                .title("Temperature log WZ-A1")
                .sourceType("WAREHOUSE")
                .normalizedPayload("{\"warehouse_zone\":\"WZ-A1\",\"sourceRecordId\":\"LOG-3003\"}")
                .build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("LOG-3003", 1L)).thenReturn(Optional.of(ceLog));

        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("LOG-3003")
                .distance(2)
                .semanticRoute("WAREHOUSE_ROUTE")
                .sourceType("WAREHOUSE")
                .title("Temperature log WZ-A1")
                .nodePath(List.of("BATCH-1010", "WZ-A1", "LOG-3003"))
                .labelPath(List.of("Batch", "Warehouse", "Evidence"))
                .relationshipPath(List.of("ASSOCIATED_WITH", "REFERENCES"))
                .intermediateEntityId("WZ-A1")
                .intermediateEntityType("Warehouse")
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("RELATED", classification.relevance());
        assertTrue(classification.discoveryReason().toLowerCase().contains("warehouse") || classification.discoveryReason().contains("WZ-A1"));

        int attached = evidenceService.attachDiscoveredEvidence(1L, 10L, "BATCH-1010", List.of(discovery));
        assertEquals(1, attached);

        ArgumentCaptor<InvestigationEvidence> captor = ArgumentCaptor.forClass(InvestigationEvidence.class);
        verify(linkRepository, atLeastOnce()).save(captor.capture());
        InvestigationEvidence saved = captor.getValue();

        assertEquals(2, saved.getDistance());
        assertEquals("RELATED", saved.getRelevance());
    }

    // D. BATCH-1010 -> WZ-A1 -> BATCH-1031 -> must NOT produce any InvestigationEvidence row
    @Test
    void testD_BatchTargetLeakagePrevention_BATCH1031() {
        // Attempt 1: Target is labeled as a Batch node
        EvidenceDiscoveryResult batchDiscovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("BATCH-1031")
                .distance(2)
                .semanticRoute("WAREHOUSE_ROUTE")
                .nodePath(List.of("BATCH-1010", "WZ-A1", "BATCH-1031"))
                .labelPath(List.of("Batch", "Warehouse", "Batch")) // Terminal node is Batch!
                .intermediateEntityId("WZ-A1")
                .intermediateEntityType("Warehouse")
                .build();

        int attached = evidenceService.attachDiscoveredEvidence(1L, 10L, "BATCH-1010", List.of(batchDiscovery));
        assertEquals(0, attached, "A Batch node target must never be attached as evidence");
        verify(linkRepository, never()).save(any());

        // Attempt 2: An MES execution record for another batch (BATCH-1031) sharing WZ-A1
        CanonicalEvidence ceMesOtherBatch = CanonicalEvidence.builder()
                .id(204L)
                .organisation(orgA)
                .externalId("SRC_MES_BATCH-1031")
                .sourceType("MES")
                .normalizedPayload("{\"batchReference\":\"BATCH-1031\",\"sourceRecordId\":\"BATCH-1031\"}")
                .build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("SRC_MES_BATCH-1031", 1L)).thenReturn(Optional.of(ceMesOtherBatch));

        EvidenceDiscoveryResult mesDiscovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("SRC_MES_BATCH-1031")
                .distance(2)
                .semanticRoute("WAREHOUSE_ROUTE")
                .sourceType("MES")
                .nodePath(List.of("BATCH-1010", "WZ-A1", "SRC_MES_BATCH-1031"))
                .labelPath(List.of("Batch", "Warehouse", "Evidence"))
                .intermediateEntityId("WZ-A1")
                .intermediateEntityType("Warehouse")
                .build();

        int attachedMes = evidenceService.attachDiscoveredEvidence(1L, 10L, "BATCH-1010", List.of(mesDiscovery));
        assertEquals(0, attachedMes, "Cross-batch MES records must never be attached to another batch's investigation");
        verify(linkRepository, never()).save(any());
    }

    // E. Traversal through a Customer -> CUSTOMER_ROUTE, not MACHINE_ROUTE
    @Test
    void testE_CustomerRouteClassification() {
        CanonicalEvidence ceCust = CanonicalEvidence.builder()
                .id(205L)
                .organisation(orgA)
                .externalId("SHIP-2058")
                .sourceType("SHIPMENT")
                .normalizedPayload("{\"customer_id\":\"CUST-1096\",\"sourceRecordId\":\"SHIP-2058\"}")
                .build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("SHIP-2058", 1L)).thenReturn(Optional.of(ceCust));

        EvidenceDiscoveryResult custDiscovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("SHIP-2058")
                .distance(2)
                .semanticRoute("CUSTOMER_ROUTE")
                .sourceType("SHIPMENT")
                .nodePath(List.of("BATCH-1010", "CUST-1096", "SHIP-2058"))
                .labelPath(List.of("Batch", "Customer", "Evidence"))
                .relationshipPath(List.of("ASSOCIATED_WITH", "REFERENCES"))
                .intermediateEntityId("CUST-1096")
                .intermediateEntityType("Customer")
                .build();

        var classification = classifier.classify(custDiscovery, "BATCH-1010");
        assertEquals("RELATED", classification.relevance());
        assertTrue(classification.discoveryReason().toLowerCase().contains("customer"));

        evidenceService.attachDiscoveredEvidence(1L, 10L, "BATCH-1010", List.of(custDiscovery));

        ArgumentCaptor<InvestigationEvidence> captor = ArgumentCaptor.forClass(InvestigationEvidence.class);
        verify(linkRepository, atLeastOnce()).save(captor.capture());
        InvestigationEvidence saved = captor.getValue();

        // Check response DTO mapping for semanticRoute
        when(linkRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(saved)));
        Page<InvestigationEvidenceResponse> page = evidenceService.listEvidence(10L, 0, 10);
        assertEquals(1, page.getContent().size());
        InvestigationEvidenceResponse resp = page.getContent().get(0);
        assertEquals("CUSTOMER_ROUTE", resp.getSemanticRoute());
        assertNotEquals("MACHINE_ROUTE", resp.getSemanticRoute());
    }

    // F. Rediscovery over an already-reviewed row -> review state preserved, no duplicate row
    @Test
    void testF_RediscoveryPreservesReviewState_NoDuplicates() {
        CanonicalEvidence ceMaint = CanonicalEvidence.builder()
                .id(202L)
                .organisation(orgA)
                .externalId("MAINT-1014")
                .sourceType("CMMS")
                .build();
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("MAINT-1014", 1L)).thenReturn(Optional.of(ceMaint));

        // Existing reviewed link
        InvestigationEvidence existingLink = InvestigationEvidence.builder()
                .id(555L)
                .organisation(orgA)
                .investigation(invA)
                .canonicalEvidence(ceMaint)
                .distance(2)
                .relevance("DIRECT") // User explicitly marked DIRECT during review
                .reviewStatus("REVIEWED")
                .investigatorNotes("Checked calibration logs for M-05 during Batch 1010 production.")
                .reviewedBy(userA)
                .reviewedAt(Instant.now())
                .build();

        when(linkRepository.findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId(1L, 10L, 202L))
                .thenReturn(Optional.of(existingLink));

        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("MAINT-1014")
                .distance(2)
                .semanticRoute("MACHINE_ROUTE")
                .sourceType("CMMS")
                .nodePath(List.of("BATCH-1010", "M-05", "MAINT-1014"))
                .labelPath(List.of("Batch", "Machine", "Evidence"))
                .relationshipPath(List.of("ASSOCIATED_WITH", "REFERENCES"))
                .intermediateEntityId("M-05")
                .intermediateEntityType("Machine")
                .build();

        int attached = evidenceService.attachDiscoveredEvidence(1L, 10L, "BATCH-1010", List.of(discovery));
        assertEquals(1, attached);

        // Verification: existing row updated in-place without duplicate, and review status/notes/relevance preserved
        assertEquals("REVIEWED", existingLink.getReviewStatus());
        assertEquals("DIRECT", existingLink.getRelevance(), "User reviewed relevance must NOT be overwritten by rediscovery");
        assertEquals("Checked calibration logs for M-05 during Batch 1010 production.", existingLink.getInvestigatorNotes());
        assertEquals(userA, existingLink.getReviewedBy());
        assertNotNull(existingLink.getReviewedAt());
    }

    // G. Discovery for org A must not return evidence reachable only through org B
    @Test
    void testG_OrganisationIsolation_NoCrossOrgEvidence() {
        // Evidence belongs to Org B (orgId 2), current user is in Org A (orgId 1)
        CanonicalEvidence ceOrgB = CanonicalEvidence.builder()
                .id(999L)
                .organisation(orgB)
                .externalId("EVID-ORGB")
                .sourceType("LIMS")
                .build();

        // Trying to find evidence by Org A should return empty
        when(canonicalRepo.findByExternalIdAndOrganisationOrgId("EVID-ORGB", 1L)).thenReturn(Optional.empty());

        EvidenceDiscoveryResult discoveryOrgB = EvidenceDiscoveryResult.builder()
                .evidenceStableId("EVID-ORGB")
                .distance(1)
                .semanticRoute("DIRECT_BATCH")
                .sourceType("LIMS")
                .nodePath(List.of("BATCH-1010", "EVID-ORGB"))
                .labelPath(List.of("Batch", "Evidence"))
                .build();

        int attached = evidenceService.attachDiscoveredEvidence(1L, 10L, "BATCH-1010", List.of(discoveryOrgB));
        assertEquals(0, attached, "Evidence belonging to another organisation must never be attached");
        verify(linkRepository, never()).save(any());
    }
}
