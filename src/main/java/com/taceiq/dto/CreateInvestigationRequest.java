package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInvestigationRequest {

    @NotBlank(message = "investigationKey is required")
    @Size(max = 100, message = "investigationKey max 100")
    private String investigationKey;

    @NotBlank(message = "title is required")
    @Size(max = 200, message = "title max 200")
    private String title;

    @Size(max = 2000, message = "description max 2000")
    private String description;

    // Incident context — optional, nullable (Incident domain alias for Investigation)
    @Size(max = 100, message = "batchReference max 100")
    private String batchReference;

    @Size(max = 100, message = "productReference max 100")
    private String productReference;

    @Size(max = 100, message = "orderReference max 100")
    private String orderReference;

    private java.time.Instant incidentStart;

    private java.time.Instant incidentEnd;
}
