package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkEvidenceRequest {
    @NotBlank(message = "stableId is required")
    private String stableId;
}
