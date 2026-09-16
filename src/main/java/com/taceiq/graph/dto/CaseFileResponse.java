package com.taceiq.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseFileResponse {

    private CaseSummary caseSummary;
    private List<GraphEvidenceResponse> evidence;
    private List<ActorSummary> actors;
    private List<RelationshipSummary> relationships;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseSummary {
        private String stableId;
        private String label;
        private long evidenceCount;
        private long actorCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorSummary {
        private String stableId;
        private String label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationshipSummary {
        private String fromStableId;
        private String fromLabel;
        private String type;
        private String toStableId;
        private String toLabel;
    }
}
