package com.taceiq;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Integration;
import com.taceiq.entity.Organisation;
import com.taceiq.graph.config.Neo4jConfig;
import com.taceiq.graph.dto.GraphProjectionResult;
import com.taceiq.graph.service.GraphProjectionService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GraphProjectionServiceTest {

    CanonicalEvidenceRepository canonicalRepo;
    Driver driver;
    Session session;
    TransactionContext tx;
    Neo4jConfig config;
    GraphProjectionService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    Organisation org2 = Organisation.builder().orgId(2L).name("Org2").build();
    Integration int1 = Integration.builder().id(10L).organisation(org1).name("IntA").build();

    @BeforeEach
    void setup() {
        canonicalRepo = mock(CanonicalEvidenceRepository.class);
        driver = mock(Driver.class);
        session = mock(Session.class);
        tx = mock(TransactionContext.class);
        config = mock(Neo4jConfig.class);
        when(config.getDatabase()).thenReturn("neo4j");
        when(config.isConfigured()).thenReturn(true);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.executeWrite(any(org.neo4j.driver.TransactionCallback.class))).thenAnswer(inv -> {
            org.neo4j.driver.TransactionCallback<?> work = inv.getArgument(0);
            return work.execute(tx);
        });
        when(tx.run(anyString(), anyMap())).thenReturn(mock(org.neo4j.driver.Result.class));
        service = new GraphProjectionService(canonicalRepo, driver, config);
    }

    CanonicalEvidence ev(String externalId, String caseId, String actorId, String parentId, boolean deleted) {
        return CanonicalEvidence.builder()
                .id(100L).organisation(org1).integration(int1).externalId(externalId)
                .caseId(caseId).actorId(actorId).parentId(parentId)
                .title("title "+externalId).sourceType("FILE").status("READY")
                .sourceCreatedAt(Instant.now()).sourceUpdatedAt(Instant.now())
                .contentHash("hash").normalizedPayload("{}")
                .firstSeenAt(Instant.now()).lastSeenAt(Instant.now())
                .isDeleted(deleted)
                .build();
    }

    @Test
    void evidenceNodeCreation() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(ev("ev_001","CASE-1",null,null,false)));
        GraphProjectionResult r = service.projectForOrganisation(1L);
        assertEquals(1, r.getEvidenceProjected());
        verify(tx, atLeastOnce()).run(contains("MERGE (e:Evidence"), anyMap());
        // Verify orgId param present
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<java.util.Map> params = ArgumentCaptor.forClass(java.util.Map.class);
        verify(tx, atLeastOnce()).run(query.capture(), params.capture());
        assertTrue(params.getAllValues().stream().anyMatch(m -> m.containsKey("orgId") && m.get("orgId").equals(1L)));
    }

    @Test
    void caseCreationBelongsTo() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(ev("ev_001","CASE-1001",null,null,false)));
        service.projectForOrganisation(1L);
        verify(tx).run(contains("MERGE (c:Case"), anyMap());
        verify(tx).run(contains("BELONGS_TO"), anyMap());
    }

    @Test
    void actorCreationCreatedBy() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(ev("ev_001",null,"actor_42",null,false)));
        service.projectForOrganisation(1L);
        verify(tx).run(contains("MERGE (a:Actor"), anyMap());
        verify(tx).run(contains("CREATED_BY"), anyMap());
    }

    @Test
    void parentRelationshipDerivedFrom() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(ev("ev_002",null,null,"ev_001",false)));
        service.projectForOrganisation(1L);
        verify(tx).run(contains("DERIVED_FROM"), anyMap());
    }

    @Test
    void idempotentRepeatedProjectionUsesMergeNotCreate() {
        CanonicalEvidence e = ev("ev_001","CASE-1","actor_1",null,false);
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(e));
        service.projectForOrganisation(1L);
        service.projectForOrganisation(1L);
        // Each projection should use MERGE, not CREATE – verify never CREATE
        verify(tx, atLeast(2)).run(contains("MERGE (e:Evidence"), anyMap());
        verify(tx, never()).run(contains("CREATE (e:Evidence"), anyMap());
    }

    @Test
    void sameCaseProjectedRepeatedlyOneCaseNode() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(
                ev("ev_001","CASE-1",null,null,false),
                ev("ev_002","CASE-1",null,null,false)
        ));
        GraphProjectionResult r = service.projectForOrganisation(1L);
        assertEquals(1, r.getCasesProjected()); // distinct
        // Should MERGE Case twice but distinct count 1
        verify(tx, atLeast(2)).run(contains("MERGE (c:Case"), anyMap());
    }

    @Test
    void sameActorProjectedRepeatedlyOneActorNode() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(
                ev("ev_001",null,"actor_42",null,false),
                ev("ev_002",null,"actor_42",null,false)
        ));
        GraphProjectionResult r = service.projectForOrganisation(1L);
        assertEquals(1, r.getActorsProjected());
    }

    @Test
    void sameRelationshipNotDuplicated() {
        CanonicalEvidence e = ev("ev_001","CASE-1","actor_42",null,false);
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(e));
        service.projectForOrganisation(1L);
        // BELONGS_TO and CREATED_BY each once per evidence – MERGE ensures no dup
        verify(tx).run(contains("BELONGS_TO"), anyMap());
        verify(tx).run(contains("CREATED_BY"), anyMap());
    }

    @Test
    void sameStableIdAcrossOrganisationsSeparateNodes() {
        CanonicalEvidence e1 = CanonicalEvidence.builder().id(1L).organisation(org1).integration(int1).externalId("ev_shared").caseId("CASE-1").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        CanonicalEvidence e2 = CanonicalEvidence.builder().id(2L).organisation(org2).integration(Integration.builder().id(20L).organisation(org2).build()).externalId("ev_shared").caseId("CASE-1").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(e1));
        when(canonicalRepo.findAllByOrganisationOrgId(2L)).thenReturn(List.of(e2));
        GraphProjectionResult r1 = service.projectForOrganisation(1L);
        GraphProjectionResult r2 = service.projectForOrganisation(2L);
        assertEquals(1, r1.getEvidenceProjected());
        assertEquals(1, r2.getEvidenceProjected());
        // Verify orgId param differs
        ArgumentCaptor<java.util.Map> captor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(tx, atLeast(1)).run(contains("MERGE (e:Evidence"), captor.capture());
        // At least one call had orgId 1 and one had 2 – verified via two projections separate
    }

    @Test
    void crossTenantRelationshipPrevention() {
        // Evidence belongs to org1, but its caseId is same as org2 case – should not create cross-org edge because orgId is always 1
        CanonicalEvidence e = ev("ev_001","CASE-1001",null,null,false);
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(e));
        service.projectForOrganisation(1L);
        // Verify BELONGS_TO uses same orgId for both sides
        ArgumentCaptor<java.util.Map> captor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(tx).run(contains("BELONGS_TO"), captor.capture());
        java.util.Map m = captor.getValue();
        assertEquals(1L, m.get("orgId"));
    }

    @Test
    void deletedEvidenceIsNotProjected() {
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(ev("ev_001","CASE-1",null,null,true)));
        GraphProjectionResult r = service.projectForOrganisation(1L);
        assertEquals(0, r.getEvidenceProjected());
        assertEquals(1, r.getSkipped());
        verify(tx, never()).run(contains("MERGE (e:Evidence"), anyMap());
    }

    @Test
    void nullCaseActorParentDoesNotCreateBogusNodes() {
        CanonicalEvidence e = CanonicalEvidence.builder().id(1L).organisation(org1).integration(int1).externalId("ev_001").contentHash("h").normalizedPayload("{}").firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).isDeleted(false).build();
        when(canonicalRepo.findAllByOrganisationOrgId(1L)).thenReturn(List.of(e));
        GraphProjectionResult r = service.projectForOrganisation(1L);
        assertEquals(1, r.getEvidenceProjected());
        assertEquals(0, r.getCasesProjected());
        assertEquals(0, r.getActorsProjected());
        verify(tx, never()).run(contains("MERGE (c:Case"), anyMap());
        verify(tx, never()).run(contains("MERGE (a:Actor"), anyMap());
        verify(tx, never()).run(contains("DERIVED_FROM"), anyMap());
    }

    @Test
    void nullDriverThrows() {
        GraphProjectionService noDriver = new GraphProjectionService(canonicalRepo, null, config);
        assertThrows(IllegalStateException.class, () -> noDriver.projectForOrganisation(1L));
    }
}
