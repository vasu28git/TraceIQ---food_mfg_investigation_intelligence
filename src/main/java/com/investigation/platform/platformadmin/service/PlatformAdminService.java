package com.investigation.platform.platformadmin.service;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.organization.dto.request.CreateOrganizationRequest;
import com.investigation.platform.organization.dto.request.UpdateOrganizationRequest;
import com.investigation.platform.organization.dto.response.OrganizationResponse;
import com.investigation.platform.platformadmin.dto.request.UpdateOrganizationStatusRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PlatformAdminService {

    OrganizationResponse createOrganization(CreateOrganizationRequest request);

    PageResponse<OrganizationResponse> getAllOrganizations(Pageable pageable);

    OrganizationResponse getOrganizationById(UUID id);

    OrganizationResponse updateOrganization(UUID id, UpdateOrganizationRequest request);

    OrganizationResponse updateOrganizationStatus(UUID id, UpdateOrganizationStatusRequest request);
}
