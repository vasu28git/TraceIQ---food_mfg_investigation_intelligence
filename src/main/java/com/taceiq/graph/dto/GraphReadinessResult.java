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
public class GraphReadinessResult {
    private Long organisationId;
    private Long integrationId;
    private Long syncId;
    private String canonicalSyncStatus;
    private boolean graphProjected;
    private boolean graphValid;
    private boolean graphReady;
    private long evidenceCount;
    private List<String> validationErrors;
    private String message;
}
