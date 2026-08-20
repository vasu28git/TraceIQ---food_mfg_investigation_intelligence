package com.investigation.platform.organization.dto.request;

import com.investigation.platform.organization.enums.OrganizationStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationRequest {

    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @Size(max = 255, message = "Domain cannot exceed 255 characters")
    private String domain;

    private String description;

    private OrganizationStatus status;
}
