package com.investigation.platform.organization.mapper;

import com.investigation.platform.organization.dto.request.CreateOrganizationRequest;
import com.investigation.platform.organization.dto.request.UpdateOrganizationRequest;
import com.investigation.platform.organization.dto.response.OrganizationResponse;
import com.investigation.platform.organization.entity.Organization;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMapper {

    public Organization toEntity(CreateOrganizationRequest request) {
        if (request == null) {
            return null;
        }
        return Organization.builder()
                .name(request.getName())
                .domain(request.getDomain())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : com.investigation.platform.organization.enums.OrganizationStatus.ACTIVE)
                .build();
    }

    public OrganizationResponse toResponse(Organization entity) {
        if (entity == null) {
            return null;
        }
        return OrganizationResponse.builder()
                .orgId(entity.getOrgId())
                .name(entity.getName())
                .domain(entity.getDomain())
                .status(entity.getStatus())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateEntityFromRequest(UpdateOrganizationRequest request, Organization entity) {
        if (request == null || entity == null) {
            return;
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDomain() != null) {
            entity.setDomain(request.getDomain());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
    }
}
