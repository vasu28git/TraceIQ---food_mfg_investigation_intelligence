package com.taceiq.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor
public class InvestigationConclusionRequest {
    private String lifecycle;
    private String outcome;
    private String confidence;
    @Size(max = 10000) private String summary;
    @Size(max = 20000) private String investigatorReasoning;
    private List<Long> supportingFindingIds;
    private List<Long> contradictingFindingIds;
}
