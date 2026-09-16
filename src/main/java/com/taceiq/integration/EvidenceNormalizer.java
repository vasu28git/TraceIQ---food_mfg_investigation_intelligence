package com.taceiq.integration;

import com.taceiq.integration.dto.ExternalEvidenceRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Component
public class EvidenceNormalizer {

    private final ObjectMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired
    public EvidenceNormalizer() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    // For tests with custom mapper
    public EvidenceNormalizer(ObjectMapper mapper) {
        this.mapper = mapper;
        this.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public static class NormalizedEvidence {
        public String externalId;
        public String caseId;
        public String actorId;
        public String parentId;
        public String title;
        public String sourceType;
        public String status;
        public Instant sourceCreatedAt;
        public Instant sourceUpdatedAt;
        public String normalizedPayload;
        public String contentHash;
        public Long size;
        public String contentType;
        public List<String> tags;
    }

    public NormalizedEvidence normalize(ExternalEvidenceRecord record) {
        if (record == null) throw new IllegalArgumentException("Record is null");
        String externalId = trim(record.getEvidenceId());
        if (externalId == null || externalId.isEmpty()) {
            throw new IllegalArgumentException("Missing required externalId (evidenceId)");
        }

        String caseId = trim(record.getCaseId());
        String title = trim(record.getTitle());
        String sourceType = trim(record.getSourceType());
        String status = trim(record.getStatus());

        String actorId = null;
        if (record.getActor() != null) actorId = trim(record.getActor().getActorId());
        if ((actorId == null || actorId.isEmpty()) && record.getRelationships() != null) {
            actorId = trim(record.getRelationships().getActorId_ref());
        }

        String parentId = null;
        if (record.getRelationships() != null) parentId = trim(record.getRelationships().getParentEvidenceId());

        Instant sourceCreatedAt = parseInstant(record.getCreatedAt());
        Instant sourceUpdatedAt = parseInstant(record.getUpdatedAt());

        Long size = null;
        String contentType = null;
        List<String> tags = null;
        String storageRef = null;
        if (record.getAttributes() != null) {
            size = record.getAttributes().getSize();
            contentType = trim(record.getAttributes().getContentType());
            storageRef = trim(record.getAttributes().getStorageRef());
            tags = record.getAttributes().getTags();
            if (tags != null) {
                List<String> trimmed = new ArrayList<>();
                for (String t : tags) if (t != null) trimmed.add(t.trim());
                tags = trimmed;
            }
        }

        // Build deterministic payload – sorted keys
        Map<String, Object> payload = new TreeMap<>();
        payload.put("evidenceId", externalId);
        if (caseId != null) payload.put("caseId", caseId);
        if (title != null) payload.put("title", title);
        if (sourceType != null) payload.put("sourceType", sourceType);
        if (status != null) payload.put("status", status);
        if (record.getCreatedAt() != null) payload.put("createdAt", trim(record.getCreatedAt()));
        if (record.getUpdatedAt() != null) payload.put("updatedAt", trim(record.getUpdatedAt()));
        if (actorId != null) payload.put("actorId", actorId);
        if (parentId != null) payload.put("parentId", parentId);
        if (size != null) payload.put("size", size);
        if (contentType != null) payload.put("contentType", contentType);
        if (storageRef != null) payload.put("storageRef", storageRef);
        if (tags != null) payload.put("tags", tags);

        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize normalized payload", e);
        }
        String hash = sha256Hex(json);

        NormalizedEvidence norm = new NormalizedEvidence();
        norm.externalId = externalId;
        norm.caseId = caseId;
        norm.actorId = actorId;
        norm.parentId = parentId;
        norm.title = title;
        norm.sourceType = sourceType;
        norm.status = status;
        norm.sourceCreatedAt = sourceCreatedAt;
        norm.sourceUpdatedAt = sourceUpdatedAt;
        norm.normalizedPayload = json;
        norm.contentHash = hash;
        norm.size = size;
        norm.contentType = contentType;
        norm.tags = tags;
        return norm;
    }

    private String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Instant parseInstant(String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        try {
            return Instant.parse(iso.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Instant: " + iso);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
