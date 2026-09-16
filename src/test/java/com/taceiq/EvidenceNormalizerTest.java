package com.taceiq;

import com.taceiq.integration.EvidenceNormalizer;
import com.taceiq.integration.dto.ExternalEvidenceRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class EvidenceNormalizerTest {

    EvidenceNormalizer normalizer = new EvidenceNormalizer();

    ExternalEvidenceRecord sample(String id, String title, String updatedAt) {
        return ExternalEvidenceRecord.builder()
                .evidenceId(id).caseId("CASE-1001").title(title).sourceType("FILE").status("READY")
                .createdAt("2026-09-01T08:12:33.000Z").updatedAt(updatedAt)
                .actor(ExternalEvidenceRecord.Actor.builder().actorId("actor_42").role("SYSTEM").build())
                .relationships(ExternalEvidenceRecord.Relationships.builder().caseId_ref("CASE-1001").actorId_ref("actor_42").parentEvidenceId(null).build())
                .attributes(ExternalEvidenceRecord.Attributes.builder().size(2048L).contentType("application/json").storageRef("REDACTED").tags(java.util.List.of("ANONYMIZED")).build())
                .build();
    }

    @Test
    void validExternalRecordMapsCorrectly() {
        ExternalEvidenceRecord rec = sample("ev_001", "Network log", "2026-09-03T10:15:00.000Z");
        EvidenceNormalizer.NormalizedEvidence n = normalizer.normalize(rec);
        assertEquals("ev_001", n.externalId);
        assertEquals("CASE-1001", n.caseId);
        assertEquals("actor_42", n.actorId);
        assertNull(n.parentId);
        assertEquals("Network log", n.title);
        assertEquals("FILE", n.sourceType);
        assertEquals("READY", n.status);
        assertNotNull(n.normalizedPayload);
        assertNotNull(n.contentHash);
    }

    @Test
    void isoTimestampsMapToInstant() {
        ExternalEvidenceRecord rec = sample("ev_001", "t", "2026-09-03T10:15:00.000Z");
        EvidenceNormalizer.NormalizedEvidence n = normalizer.normalize(rec);
        assertEquals(Instant.parse("2026-09-01T08:12:33.000Z"), n.sourceCreatedAt);
        assertEquals(Instant.parse("2026-09-03T10:15:00.000Z"), n.sourceUpdatedAt);
    }

    @Test
    void externalIdIsPreserved() {
        ExternalEvidenceRecord rec = sample("ev_123", "t", "2026-09-03T10:15:00.000Z");
        assertEquals("ev_123", normalizer.normalize(rec).externalId);
    }

    @Test
    void relationshipIdsMapCorrectly() {
        ExternalEvidenceRecord rec = ExternalEvidenceRecord.builder()
                .evidenceId("ev_002").caseId("CASE-1001").title("Endpoint snapshot").sourceType("API").status("READY")
                .createdAt("2026-09-02T09:00:00.000Z").updatedAt("2026-09-03T10:16:12.000Z")
                .actor(ExternalEvidenceRecord.Actor.builder().actorId("actor_07").role("ANALYST").build())
                .relationships(ExternalEvidenceRecord.Relationships.builder().caseId_ref("CASE-1001").actorId_ref("actor_07").parentEvidenceId("ev_001").build())
                .attributes(ExternalEvidenceRecord.Attributes.builder().size(5120L).contentType("application/json").tags(java.util.List.of("ANONYMIZED")).build())
                .build();
        EvidenceNormalizer.NormalizedEvidence n = normalizer.normalize(rec);
        assertEquals("ev_001", n.parentId);
        assertEquals("actor_07", n.actorId);
        assertEquals("CASE-1001", n.caseId);
    }

    @Test
    void normalizedPayloadIsDeterministic() {
        ExternalEvidenceRecord r1 = sample("ev_001", "Network log", "2026-09-03T10:15:00.000Z");
        ExternalEvidenceRecord r2 = sample("ev_001", "Network log", "2026-09-03T10:15:00.000Z");
        EvidenceNormalizer.NormalizedEvidence n1 = normalizer.normalize(r1);
        EvidenceNormalizer.NormalizedEvidence n2 = normalizer.normalize(r2);
        assertEquals(n1.normalizedPayload, n2.normalizedPayload);
        assertEquals(n1.contentHash, n2.contentHash);
    }

    @Test
    void sameRecordGeneratesSameHash() {
        ExternalEvidenceRecord rec = sample("ev_001", "same", "2026-09-03T10:15:00.000Z");
        String h1 = normalizer.normalize(rec).contentHash;
        String h2 = normalizer.normalize(rec).contentHash;
        assertEquals(h1, h2);
        assertEquals(64, h1.length()); // SHA-256 hex
    }

    @Test
    void changedMeaningfulFieldChangesHash() {
        ExternalEvidenceRecord r1 = sample("ev_001", "title A", "2026-09-03T10:15:00.000Z");
        ExternalEvidenceRecord r2 = sample("ev_001", "title B", "2026-09-03T10:15:00.000Z");
        assertNotEquals(normalizer.normalize(r1).contentHash, normalizer.normalize(r2).contentHash);
        // also caseId change
        ExternalEvidenceRecord r3 = sample("ev_001", "title A", "2026-09-03T10:16:00.000Z");
        assertNotEquals(normalizer.normalize(r1).contentHash, normalizer.normalize(r3).contentHash);
    }

    @Test
    void missingExternalIdIsRejected() {
        ExternalEvidenceRecord rec = ExternalEvidenceRecord.builder().evidenceId("  ").caseId("CASE-1").build();
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(rec));
        ExternalEvidenceRecord rec2 = ExternalEvidenceRecord.builder().evidenceId(null).build();
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(rec2));
    }

    @Test
    void trimHandling() {
        ExternalEvidenceRecord rec = ExternalEvidenceRecord.builder()
                .evidenceId("  ev_001  ").caseId("  CASE-1001 ").title("  t  ").sourceType("  FILE ").status("  READY ")
                .createdAt("2026-09-01T08:12:33.000Z").updatedAt("2026-09-03T10:15:00.000Z")
                .build();
        EvidenceNormalizer.NormalizedEvidence n = normalizer.normalize(rec);
        assertEquals("ev_001", n.externalId);
        assertEquals("CASE-1001", n.caseId);
        assertEquals("t", n.title);
        assertEquals("FILE", n.sourceType);
        assertEquals("READY", n.status);
    }
}
