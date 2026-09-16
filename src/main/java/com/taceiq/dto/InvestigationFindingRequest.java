package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class InvestigationFindingRequest {
    @NotBlank @Size(max = 2000) private String statement;
    @Size(max = 200) private String category;
    private String confidence;
    private String status;
    @Size(max = 5000) private String reasoning;
    private List<EvidenceLinkRequest> evidence;

    @Getter @Setter
    public static class EvidenceLinkRequest {
        @NotBlank private String stableId;
        private String relationshipType;
    }
}