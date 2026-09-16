package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFindingRequest {

    @NotBlank(message = "title is required")
    @Size(max = 200, message = "title max 200")
    private String title;

    @Size(max = 2000, message = "description max 2000")
    private String description;

    @Size(max = 5000, message = "conclusion max 5000")
    private String conclusion;

    @Size(max = 20, message = "status max 20")
    private String status; // OPEN, CONFIRMED, DISMISSED — optional, defaults to OPEN

    // Provenance parameters
    private String sourceSignalKey;
    private String sourcePathId;
    private String sourceEntity;
    private String sourceEvidenceId;
    private String sourceSystem;
    private String connectionPath;
}
