package com.taceiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationPathDto {
    private String id;
    private String title;
    private String priority; // HIGH, MEDIUM, LOW
    private String reason;
    private List<String> path;
    private String targetEvidenceId;
    private String targetEntityId;
    private int signalCount;
}
