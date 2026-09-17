package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEvidenceRequest {
    @NotBlank
    private String reviewStatus; // REVIEWED, REJECTED, PENDING_REVIEW

    @Size(max = 5000)
    private String investigatorNotes;
}
