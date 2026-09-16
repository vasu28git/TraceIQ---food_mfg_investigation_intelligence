package com.taceiq.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateInvestigationCheckRequest {
    @Size(max = 200, message = "title max 200")
    private String title;

    @Size(max = 2000, message = "description max 2000")
    private String description;
}
