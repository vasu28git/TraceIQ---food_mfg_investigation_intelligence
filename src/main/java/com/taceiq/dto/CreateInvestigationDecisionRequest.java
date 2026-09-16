package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInvestigationDecisionRequest {
    @NotBlank(message = "decisionKey is required")
    @Size(max = 100, message = "decisionKey max 100")
    private String decisionKey;

    @NotBlank(message = "title is required")
    @Size(max = 200, message = "title max 200")
    private String title;

    @NotBlank(message = "conclusion is required")
    @Size(max = 5000, message = "conclusion max 5000")
    private String conclusion;

    @Size(max = 5000, message = "rationale max 5000")
    private String rationale;
}
