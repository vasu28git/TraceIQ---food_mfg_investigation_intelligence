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
public class GraphEvidenceDetailResponse {
    private String stableId;
    private String title;
    private String sourceType;
    private String status;
    private String sourceCreatedAt;
    private String sourceUpdatedAt;
    private String caseId;
    private String actorId;
    private String parentId;
    private String firstSeenAt;
    private String lastSeenAt;
    private Attributes attributes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attributes {
        private Long size;
        private String contentType;
        private List<String> tags;
    }
}
