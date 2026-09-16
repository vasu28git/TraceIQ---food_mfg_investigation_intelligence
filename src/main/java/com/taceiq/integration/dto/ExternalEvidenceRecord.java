package com.taceiq.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalEvidenceRecord {

    private String evidenceId;
    private String caseId;
    private String title;
    private String sourceType;
    private String status;
    private String createdAt;
    private String updatedAt;

    private Actor actor;
    private Relationships relationships;
    private Attributes attributes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Actor {
        private String actorId;
        private String role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Relationships {
        private String caseId_ref;
        private String actorId_ref;
        private String parentEvidenceId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {
        private Long size;
        private String contentType;
        private String storageRef;
        private List<String> tags;
    }
}
