package com.taceiq.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteInvestigationCheckRequest {
    @Size(max = 2000, message = "result max 2000")
    private String result;

    @Size(max = 5000, message = "notes max 5000")
    private String notes;
}
