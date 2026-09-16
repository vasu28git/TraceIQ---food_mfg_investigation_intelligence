package com.taceiq.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;

@Getter @Builder
public class InvestigationFindingResponse {
    private Long id;
    private Long investigationId;
    private String statement;
    private String category;
    private String confidence;
    private String status;
    private String reasoning;
    private Long createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private List<LinkedEvidence> evidence;

    @Getter @Builder
    public static class LinkedEvidence {
        private String stableId;
        private String sourceType;
        private String title;
        private String relationshipType;
        private String reviewStatus;
        private String relevance;
        private String assessment;
    }
}