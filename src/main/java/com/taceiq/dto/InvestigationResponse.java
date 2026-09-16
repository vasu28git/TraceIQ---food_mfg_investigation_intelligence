package com.taceiq.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationResponse {
    private Long id;
    private String investigationKey;
    private String complaintKey;
    private String title;
    private String description;
    // Incident context — optional (Incident domain alias for Investigation persistence)
    private String batchReference;
    private String productReference;
    private String orderReference;
    private Instant incidentStart;
    private Instant incidentEnd;
    private String status;
    private Long createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;

    public static InvestigationResponse fromEntity(com.taceiq.entity.Investigation e) {
        return InvestigationResponse.builder()
                .id(e.getId())
                .investigationKey(e.getInvestigationKey())
                .title(e.getTitle())
                .description(e.getDescription())
                .batchReference(e.getBatchReference())
                .productReference(e.getProductReference())
                .orderReference(e.getOrderReference())
                .incidentStart(e.getIncidentStart())
                .incidentEnd(e.getIncidentEnd())
                .status(e.getStatus())
                .createdByUserId(e.getCreatedBy() != null ? e.getCreatedBy().getId() : null)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
