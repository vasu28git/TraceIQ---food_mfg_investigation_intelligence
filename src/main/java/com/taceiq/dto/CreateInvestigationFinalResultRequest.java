package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInvestigationFinalResultRequest {
    @NotBlank(message = "outcome is required")
    @Size(max = 100, message = "outcome max 100")
    private String outcome;

    @NotBlank(message = "conclusion is required")
    @Size(max = 10000, message = "conclusion max 10000")
    private String conclusion;

    @Size(max = 10000, message = "rationale max 10000")
    private String rationale;
}
