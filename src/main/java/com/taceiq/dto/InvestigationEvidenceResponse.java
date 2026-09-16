package com.taceiq.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationEvidenceResponse {
    private String stableId;
    private String title;
    private String sourceType;
    private String status;
    private Instant linkedAt;
    private String correlationReason;
    private String batchReference;
    private String sourceRecordId;
    private String machineReference;
    private String supplierReference;
    private String productReference;
    private String orderReference;
    private String originalFileName;
    private Long fileId;
    private String contentType;
    private Long size;
    private String associationType;
    private String normalizedPayload;
    private List<EvidenceMatchExplanation> matchExplanations;
    private String reviewStatus;
    private String relevance;
    private String importance;
    private String assessment;
    private String investigatorNotes;
    private Long reviewedByUserId;
    private java.time.Instant reviewedAt;

    public static InvestigationEvidenceResponse fromLink(com.taceiq.entity.InvestigationEvidence link) {
        var ev = link.getCanonicalEvidence();
        String payload = ev.getNormalizedPayload();
        String corr = extractCorrelation(payload);
        String batch = extractField(payload, "batchReference", "batch_id", "batchId", "batch_reference");
        String recId = extractField(payload, "sourceRecordId", "source_record_id");
        String prod = extractField(payload, "productReference", "product_id", "productId", "product_reference");
        String mach = extractField(payload, "machineReference", "machine_id", "machineId", "machine_reference");
        String supp = extractField(payload, "supplierReference", "supplier_id", "supplierId", "supplier_reference");

        return InvestigationEvidenceResponse.builder()
                .stableId(ev.getExternalId())
                .title(ev.getTitle())
                .sourceType(ev.getSourceType())
                .status(ev.getStatus())
                .linkedAt(link.getCreatedAt())
                .correlationReason(corr)
                .batchReference(batch)
                .productReference(prod)
                .machineReference(mach)
                .supplierReference(supp)
                .sourceRecordId(recId)
                .normalizedPayload(payload)
                .build();
    }

    private static String extractCorrelation(String payload) {
        if (payload == null) return null;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(payload);
            if (node.has("correlationReason")) return node.get("correlationReason").asText();
        } catch (Exception ignored) {}
        return null;
    }

    private static String extractField(String payload, String... keys) {
        if (payload == null) return null;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(payload);
            for (String key : keys) {
                if (node.has(key) && !node.get(key).isNull()) return node.get(key).asText();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
