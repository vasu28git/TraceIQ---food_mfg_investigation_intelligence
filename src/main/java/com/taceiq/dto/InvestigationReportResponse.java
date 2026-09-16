package com.taceiq.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationReportResponse {

    private ReportMetadata reportMetadata;
    private ComplaintResponse complaint;
    private InvestigationResponse investigation;
    private InvestigationTimelineResponse timeline;
    private List<InvestigationEvidenceResponse> evidence;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportMetadata {
        private Long investigationId;
        private String investigationKey;
        private String title;
        private Instant generatedAt;
        private String format;
        private String status;
    }
}

