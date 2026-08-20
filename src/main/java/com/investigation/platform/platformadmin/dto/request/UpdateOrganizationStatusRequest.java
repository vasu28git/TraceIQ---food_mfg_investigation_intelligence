package com.investigation.platform.platformadmin.dto.request;

import com.investigation.platform.organization.enums.OrganizationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationStatusRequest {

    @NotNull(message = "Organization status is required")
    private OrganizationStatus status;
}
