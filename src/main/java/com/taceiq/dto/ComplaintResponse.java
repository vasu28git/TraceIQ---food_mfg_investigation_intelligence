package com.taceiq.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintResponse {
    private Long id;
    private String complaintKey;
    private String title;
    private String description;
    private String batchReference;
    private String externalReference;
    private String sourceType;
    private String raisedAt;
    private String receivedAt;
    private Long createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private InvestigationSummary investigation;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestigationSummary {
        private Long id;
        private String investigationKey;
        private String title;
        private String status;
    }

    public static ComplaintResponse fromEntity(com.taceiq.entity.Complaint c) {
        InvestigationSummary inv = null;
        if (c.getInvestigation() != null) {
            inv = InvestigationSummary.builder()
                    .id(c.getInvestigation().getId())
                    .investigationKey(c.getInvestigation().getInvestigationKey())
                    .title(c.getInvestigation().getTitle())
                    .status(c.getInvestigation().getStatus())
                    .build();
        }
        return ComplaintResponse.builder()
                .id(c.getId())
                .complaintKey(c.getComplaintKey())
                .title(c.getTitle())
                .description(c.getDescription())
                .batchReference(c.getBatchReference())
                .externalReference(c.getExternalReference())
                .sourceType(c.getSourceType())
                .raisedAt(c.getRaisedAt() != null ? c.getRaisedAt().toString() : null)
                .receivedAt(c.getReceivedAt() != null ? c.getReceivedAt().toString() : null)
                .createdByUserId(c.getCreatedBy() != null ? c.getCreatedBy().getId() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .investigation(inv)
                .build();
    }
}
