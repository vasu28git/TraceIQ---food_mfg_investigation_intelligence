package com.taceiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchConnectionMapResponse {
    private Long incidentId;
    private String batchReference;
    private Map<String, Integer> counts;
    private List<ConnectionNode> nodes;
    private List<ConnectionEdge> relationships;
    private List<InvestigationEvidenceResponse> evidence;
    private List<InvestigationSignalDto> signals;
    private List<InvestigationPathDto> paths;
    // CROSS-BATCH CONTEXT - separated from primary investigation
    private Map<String, Integer> crossBatchCounts;
    private List<ConnectionNode> crossBatchNodes;
    private List<ConnectionEdge> crossBatchRelationships;
    private List<InvestigationEvidenceResponse> crossBatchEvidence;
    private List<RelatedBatchInfo> relatedBatches;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionNode {
        private String key;
        private String type;
        private String stableId;
        private String label;
        private String sourceType;
        private boolean evidence;
        private boolean direct;
        private String priority; // HIGH, MEDIUM, LOW
        private int signalCount;
        private List<String> signalTypes;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionEdge {
        private String from;
        private String to;
        private String type;
        private boolean direct;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedBatchInfo {
        private String batchReference;
        private String sharedAttributeType; // Product, Machine, Supplier
        private String sharedAttributeValue;
        private String reason; // e.g., "SHARED_PRODUCT"
        private int evidenceCount;
        private List<String> evidenceIds;
    }
}