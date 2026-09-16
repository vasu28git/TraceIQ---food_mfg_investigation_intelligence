package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationNoteRequest {
    @NotBlank(message = "content is required")
    @Size(max = 5000, message = "content max 5000")
    private String content;
}
