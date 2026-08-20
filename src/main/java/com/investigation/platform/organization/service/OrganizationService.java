package com.investigation.platform.organization.service;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.organization.dto.request.CreateOrganizationRequest;
import com.investigation.platform.organization.dto.request.UpdateOrganizationRequest;
import com.investigation.platform.organization.dto.response.OrganizationResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrganizationService {

    OrganizationResponse createOrganization(CreateOrganizationRequest request);

    OrganizationResponse getOrganizationById(UUID orgId);

    OrganizationResponse updateOrganization(UUID orgId, UpdateOrganizationRequest request);

    PageResponse<OrganizationResponse> getAllOrganizations(Pageable pageable);

    void deleteOrganization(UUID orgId);
}
