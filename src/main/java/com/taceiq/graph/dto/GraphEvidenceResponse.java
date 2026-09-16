package com.taceiq.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEvidenceResponse {
    private String stableId;
    private String title;
    private String sourceType;
    private String status;
    private String sourceCreatedAt;
    private String sourceUpdatedAt;
    private String batchReference;
    private String sourceRecordId;
}
