package com.taceiq.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class TraceabilityDtos {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TraceabilityNodeResponse {
        private String stableId;
        private String label;
        private String title;
        private String sourceType;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TraceabilityRelationshipResponse {
        private String fromStableId;
        private String fromLabel;
        private String type;
        private String toStableId;
        private String toLabel;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CaseTraceabilityResponse {
        private TraceabilityNodeResponse caseNode;
        private List<TraceabilityNodeResponse> nodes;
        private List<TraceabilityRelationshipResponse> relationships;
        private int depth;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IncidentTraceabilityResponse {
        private TraceabilityNodeResponse incidentNode;
        private List<TraceabilityNodeResponse> nodes;
        private List<TraceabilityRelationshipResponse> relationships;
        private int depth;
    }
}
