package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateComplaintRequest {

    @NotBlank(message = "complaintKey is required")
    @Size(max = 100, message = "complaintKey max 100")
    private String complaintKey;

    @NotBlank(message = "title is required")
    @Size(max = 200, message = "title max 200")
    private String title;

    @Size(max = 2000, message = "description max 2000")
    private String description;

    @Size(max = 100, message = "batchReference max 100")
    private String batchReference;

    @Size(max = 200, message = "externalReference max 200")
    private String externalReference;

    private String raisedAt;
}
