package com.taceiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationSignalDto {
    private String id;
    private String sourceEvidenceId;
    private String sourceSystem;
    private String exactField;
    private String actualValue;
    private String expectedValue;
    private String signalType; // CRITICAL, WARNING, INFO
    private String deterministicReason;
    private String entityType;
    private String entityId;
    private List<String> connectionPath;
    private Instant discoveredAt;
}
