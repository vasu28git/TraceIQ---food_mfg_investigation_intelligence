package com.taceiq.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkipInvestigationCheckRequest {
    @Size(max = 5000, message = "notes max 5000")
    private String notes;
}
