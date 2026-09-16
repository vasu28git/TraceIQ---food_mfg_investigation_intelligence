package com.taceiq.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter @Builder
public class FindingEvidenceTraceabilityResponse {
    private Long investigationId;
    private Long findingId;
    private List<EvidenceTrace> evidence;

    @Getter @Builder
    public static class EvidenceTrace {
        private String evidenceId;
        private String sourceSystem;
        private String sourceRecordId;
        private String title;
        private String findingRelationship;
        private List<DiscoveryPath> discoveryPaths;
        private SourceRecord sourceRecord;
    }

    @Getter @Builder
    public static class DiscoveryPath {
        private String classification;
        private String reason;
        private String matchedField;
        private String matchedValue;
        private String intermediateEntityType;
        private String intermediateEntityValue;
        private List<String> path;
        private Instant discoveredAt;
    }

    @Getter @Builder
    public static class SourceRecord {
        private Long id;
        private String sourceType;
        private String sourceRecordId;
        private String batchReference;
        private String machineReference;
        private String supplierReference;
        private String productReference;
        private String orderReference;
        private String externalReference;
        private String payload;
        private Instant ingestedAt;
        private SourceFile sourceFile;
    }

    @Getter @Builder
    public static class SourceFile {
        private Long id;
        private String originalName;
        private String contentType;
        private Long size;
        private Instant receivedAt;
    }
}
