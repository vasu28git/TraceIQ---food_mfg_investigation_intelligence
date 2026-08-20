package com.investigation.platform.organization.dto.request;

import com.investigation.platform.organization.enums.OrganizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationRequest {

    @NotBlank(message = "Organization name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @Size(max = 255, message = "Domain cannot exceed 255 characters")
    private String domain;

    private String description;

    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.ACTIVE;
}
