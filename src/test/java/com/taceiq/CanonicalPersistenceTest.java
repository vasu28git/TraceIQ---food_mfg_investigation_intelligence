package com.taceiq;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Integration;
import com.taceiq.entity.IntegrationSync;
import com.taceiq.entity.Organisation;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.IntegrationSyncRepository;
import com.taceiq.repository.OrganisationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:canonicaltest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false"
})
class CanonicalPersistenceTest {

    @Autowired CanonicalEvidenceRepository canonicalRepo;
    @Autowired IntegrationSyncRepository syncRepo;
    @Autowired IntegrationRepository integrationRepo;
    @Autowired OrganisationRepository orgRepo;

    Organisation newOrg(String name) {
        Organisation o = Organisation.builder().name(name).domain(name.toLowerCase()+".com").status("ACTIVE").description("test").build();
        return orgRepo.save(o);
    }

    Integration newIntegration(Organisation org, String name) {
        Integration i = Integration.builder().name(name).type("API").status("ACTIVE").configuration("{\"url\":\"https://example.com\"}").organisation(org).build();
        return integrationRepo.save(i);
    }

    CanonicalEvidence newEvidence(Organisation org, Integration integ, String extId, String hash) {
        return CanonicalEvidence.builder()
                .organisation(org).integration(integ)
                .externalId(extId).contentHash(hash)
                .normalizedPayload("{\"evidenceId\":\""+extId+"\"}")
                .caseId("CASE-1001").title("title "+extId)
                .sourceType("FILE").status("READY")
                .build();
    }

    @Test
    void integrationSyncCanPersist() {
        Organisation org = newOrg("SyncOrg1");
        Integration integ = newIntegration(org, "IntSync1");
        IntegrationSync sync = IntegrationSync.builder()
                .organisation(org).integration(integ)
                .status("PENDING").recordsFetched(0).recordsCreated(0)
                .build();
        IntegrationSync saved = syncRepo.save(sync);
        assertNotNull(saved.getId());
        assertEquals("PENDING", saved.getStatus());
        Optional<IntegrationSync> found = syncRepo.findByIdAndOrganisationOrgId(saved.getId(), org.getOrgId());
        assertTrue(found.isPresent());
        // tenant isolation
        assertTrue(syncRepo.findByIdAndOrganisationOrgId(saved.getId(), 999999L).isEmpty());
    }

    @Test
    void canonicalEvidenceCanPersist() {
        Organisation org = newOrg("EvOrg1");
        Integration integ = newIntegration(org, "EvInt1");
        CanonicalEvidence ev = newEvidence(org, integ, "ev_001", "hash1");
        CanonicalEvidence saved = canonicalRepo.save(ev);
        assertNotNull(saved.getId());
        assertEquals("ev_001", saved.getExternalId());
    }

    @Test
    void uniqueConstraintWorks() {
        Organisation org = newOrg("UniqOrg");
        Integration integ = newIntegration(org, "UniqInt");
        CanonicalEvidence ev1 = newEvidence(org, integ, "ev_dup", "h1");
        canonicalRepo.save(ev1);
        CanonicalEvidence ev2 = newEvidence(org, integ, "ev_dup", "h2");
        assertThrows(DataIntegrityViolationException.class, () -> {
            canonicalRepo.saveAndFlush(ev2);
        });
    }

    @Test
    void sameExternalIdCanExistForDifferentOrganisations() {
        Organisation orgA = newOrg("OrgA_diff");
        Organisation orgB = newOrg("OrgB_diff");
        Integration intA = newIntegration(orgA, "IntA_dup");
        Integration intB = newIntegration(orgB, "IntB_dup");
        CanonicalEvidence evA = newEvidence(orgA, intA, "ev_shared", "hA");
        CanonicalEvidence evB = newEvidence(orgB, intB, "ev_shared", "hB");
        canonicalRepo.save(evA);
        CanonicalEvidence savedB = canonicalRepo.save(evB);
        assertNotNull(savedB.getId());
        assertEquals(1, canonicalRepo.findByIntegrationIdAndOrganisationOrgId(intA.getId(), orgA.getOrgId()).size());
        assertEquals(1, canonicalRepo.findByIntegrationIdAndOrganisationOrgId(intB.getId(), orgB.getOrgId()).size());
    }

    @Test
    void sameExternalIdCanExistForDifferentIntegrations() {
        Organisation org = newOrg("OrgMultiInt");
        Integration int1 = newIntegration(org, "Int1_multi");
        Integration int2 = newIntegration(org, "Int2_multi");
        CanonicalEvidence ev1 = newEvidence(org, int1, "ev_multi", "h1");
        CanonicalEvidence ev2 = newEvidence(org, int2, "ev_multi", "h2");
        CanonicalEvidence s1 = canonicalRepo.save(ev1);
        CanonicalEvidence s2 = canonicalRepo.save(ev2);
        assertNotNull(s1.getId());
        assertNotNull(s2.getId());
        Optional<CanonicalEvidence> f1 = canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_multi", int1.getId(), org.getOrgId());
        Optional<CanonicalEvidence> f2 = canonicalRepo.findByExternalIdAndIntegrationIdAndOrganisationOrgId("ev_multi", int2.getId(), org.getOrgId());
        assertTrue(f1.isPresent());
        assertTrue(f2.isPresent());
        assertNotEquals(f1.get().getId(), f2.get().getId());
    }

    @Test
    void tenantScopedMethodsDoNotCrossOrganisations() {
        Organisation orgA = newOrg("TenantA");
        Organisation orgB = newOrg("TenantB");
        Integration intA = newIntegration(orgA, "IntTenantA");
        Integration intB = newIntegration(orgB, "IntTenantB");
        CanonicalEvidence evA = newEvidence(orgA, intA, "ev_tenant_A", "hA");
        evA.setCaseId("CASE-1001"); evA.setActorId("actor_1");
        CanonicalEvidence evB = newEvidence(orgB, intB, "ev_tenant_B", "hB");
        evB.setCaseId("CASE-1001"); evB.setActorId("actor_1");
        canonicalRepo.save(evA);
        canonicalRepo.save(evB);

        List<CanonicalEvidence> listA = canonicalRepo.findAllByOrganisationOrgId(orgA.getOrgId());
        assertEquals(1, listA.size());
        assertEquals("ev_tenant_A", listA.get(0).getExternalId());

        List<CanonicalEvidence> caseA = canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1001", orgA.getOrgId());
        assertEquals(1, caseA.size());
        assertTrue(canonicalRepo.findByCaseIdAndOrganisationOrgId("CASE-1001", 999999L).isEmpty());

        List<CanonicalEvidence> actorB = canonicalRepo.findByActorIdAndOrganisationOrgId("actor_1", orgB.getOrgId());
        assertEquals(1, actorB.size());

        // sync tenant isolation
        IntegrationSync syncA = IntegrationSync.builder().organisation(orgA).integration(intA).status("PENDING").build();
        syncRepo.save(syncA);
        assertTrue(syncRepo.findByIdAndOrganisationOrgId(syncA.getId(), orgA.getOrgId()).isPresent());
        assertTrue(syncRepo.findByIdAndOrganisationOrgId(syncA.getId(), orgB.getOrgId()).isEmpty());
        assertEquals(0, syncRepo.findAllByOrganisationOrgId(orgB.getOrgId()).size());
    }

    @Test
    void canonicalIndexesAndNullableFields() {
        Organisation org = newOrg("IdxOrg");
        Integration integ = newIntegration(org, "IdxInt");
        CanonicalEvidence ev = CanonicalEvidence.builder()
                .organisation(org).integration(integ)
                .externalId("ev_idx").contentHash("hidx")
                .normalizedPayload("{\"x\":1}").caseId(null).actorId(null).parentId("ev_parent")
                .build();
        CanonicalEvidence saved = canonicalRepo.save(ev);
        assertNotNull(saved.getId());
        assertNull(saved.getCaseId());
        List<CanonicalEvidence> byParent = canonicalRepo.findByParentIdAndOrganisationOrgId("ev_parent", org.getOrgId());
        assertEquals(1, byParent.size());
    }
}
