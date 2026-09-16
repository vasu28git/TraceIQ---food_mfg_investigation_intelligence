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
public class GraphValidationResult {
    private boolean valid;
    private long evidenceCount;
    private long invalidCount;
    private List<String> errors;
}
