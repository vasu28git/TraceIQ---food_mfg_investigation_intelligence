package com.taceiq.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InvestigationEvidenceAssessmentRequest {
    private String reviewStatus;
    private String relevance;
    private String importance;
    private String assessment;

    @Size(max = 5000)
    private String investigatorNotes;
}