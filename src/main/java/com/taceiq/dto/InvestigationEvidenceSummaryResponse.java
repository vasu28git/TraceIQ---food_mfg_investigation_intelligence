package com.taceiq.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class InvestigationEvidenceSummaryResponse {
    private Long investigationId;
    private long totalEvidence;
    private long reviewed;
    private long pendingReview;
    private Set<String> sourceSystems;
}
