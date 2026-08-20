package com.investigation.platform.investigation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FUTURE MODULE: Case DTO Placeholder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseSummaryDto {

    private UUID caseId;
    private UUID orgId;
    private String title;
    private String status;
    private UUID assignedInvestigator;
    private List<String> tags;
    private Instant createdAt;
}
