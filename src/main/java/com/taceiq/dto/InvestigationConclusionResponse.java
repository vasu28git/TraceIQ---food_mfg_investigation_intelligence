package com.taceiq.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter @Builder
public class InvestigationConclusionResponse {
    private Long id;
    private Long investigationId;
    private String lifecycle;
    private String outcome;
    private String confidence;
    private String summary;
    private String investigatorReasoning;
    private Long createdByUserId;
    private Long updatedByUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant finalizedAt;
    private List<LinkedFinding> supportingFindings;
    private List<LinkedFinding> contradictingFindings;

    @Getter @Builder
    public static class LinkedFinding {
        private Long id;
        private String statement;
        private String category;
        private String confidence;
        private String status;
        private String reasoning;
        private String relationshipType;
        private List<LinkedEvidence> evidence;
    }

    @Getter @Builder
    public static class LinkedEvidence {
        private String stableId;
        private String title;
        private String sourceType;
        private String relationshipType;
    }
}
