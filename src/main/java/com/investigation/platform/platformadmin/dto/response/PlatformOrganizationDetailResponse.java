package com.investigation.platform.platformadmin.dto.response;

import com.investigation.platform.organization.dto.response.OrganizationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformOrganizationDetailResponse {

    private OrganizationResponse organization;
    private long totalUsers;
    private long totalIntegrations;
    private long totalFiles;
    private UUID primaryAdminId;
}
