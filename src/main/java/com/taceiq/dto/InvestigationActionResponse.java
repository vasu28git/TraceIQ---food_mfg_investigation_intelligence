package com.taceiq.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.time.LocalDate;

@Getter @Builder
public class InvestigationActionResponse {
    private Long id;
    private Long investigationId;
    private String title;
    private String description;
    private String actionType;
    private Long ownerUserId;
    private String ownerUsername;
    private String priority;
    private LocalDate dueDate;
    private String status;
    private String notes;
    private Long findingId;
    private String findingStatement;
    private Long conclusionId;
    private String conclusionSummary;
    private Long createdByUserId;
    private Long updatedByUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
