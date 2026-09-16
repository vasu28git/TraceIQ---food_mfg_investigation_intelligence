package com.taceiq.dto;

import lombok.*;

/**
 * Evidence Chronology event.
 * Current evidence ingestion does not provide structured domain event/action history.
 * Therefore the timeline currently represents deterministic evidence creation/update chronology.
 * Richer actions such as LOGIN, RECORD_ACCESSED, or RECORD_EXPORTED require the upstream
 * evidence source to provide structured event/action data or history.
 * See docs/sample-source-response.json – only sourceCreatedAt/sourceUpdatedAt are available.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationTimelineEventResponse {
    private String eventType;
    private String eventTime;
    private String title;
    private String description;
    private String sourceType;
    private String sourceId;
    private String stableId;
    private Object metadata;
    // Safe evidence context – from canonical_evidence (never orgId/contentHash/storageRef/raw payload)
    private String caseId;
    private String actorId;
    private String parentId;
    private String status;
    private Long size;
    private String contentType;
    private java.util.List<String> tags;
}
