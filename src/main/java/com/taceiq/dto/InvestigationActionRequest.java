package com.taceiq.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor
public class InvestigationActionRequest {
    private String title;
    @Size(max = 10000) private String description;
    private String actionType;
    private Long ownerUserId;
    private String priority;
    private LocalDate dueDate;
    private String status;
    @Size(max = 10000) private String notes;
    private Long findingId;
    private Long conclusionId;
}
