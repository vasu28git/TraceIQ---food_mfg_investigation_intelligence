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
public class EvidenceMatchExplanation {
    private String reason;
    private String matchedField;
    private String matchedValue;
    private String sourceRecordId;
    private String intermediateEntityType;
    private String intermediateEntityValue;
    private List<String> connectionPath;
    private Instant discoveredAt;
}