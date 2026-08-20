package com.investigation.platform.organization.service.impl;

import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.audit.service.AuditLogService;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.exception.BadRequestException;
import com.investigation.platform.exception.ResourceNotFoundException;
import com.investigation.platform.organization.dto.request.CreateOrganizationRequest;
import com.investigation.platform.organization.dto.request.UpdateOrganizationRequest;
import com.investigation.platform.organization.dto.response.OrganizationResponse;
import com.investigation.platform.organization.entity.Organization;
import com.investigation.platform.organization.mapper.OrganizationMapper;
import com.investigation.platform.organization.repository.OrganizationRepository;
import com.investigation.platform.organization.service.OrganizationBootstrapService;
import com.investigation.platform.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;
    private final OrganizationBootstrapService organizationBootstrapService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        if (StringUtils.hasText(request.getDomain()) && organizationRepository.existsByDomain(request.getDomain())) {
            throw new BadRequestException("Organization domain already exists: " + request.getDomain());
        }

        Organization org = organizationMapper.toEntity(request);
        Organization savedOrg = organizationRepository.save(org);
        log.info("Organization created with ID: {}", savedOrg.getOrgId());

        // Bootstrap default roles & permissions
        organizationBootstrapService.bootstrapOrganization(
                savedOrg.getOrgId(),
                "admin@" + (StringUtils.hasText(savedOrg.getDomain()) ? savedOrg.getDomain() : "tenant.local"),
                "Organization Admin",
                "Admin@123456"
        );

        auditLogService.recordEvent(
                savedOrg.getOrgId(),
                null,
                AuditAction.ORGANIZATION_CREATED,
                "ORGANIZATION",
                savedOrg.getOrgId().toString(),
                "Organization created: " + savedOrg.getName()
        );

        return organizationMapper.toResponse(savedOrg);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "orgId", orgId));
        return organizationMapper.toResponse(org);
    }

    @Override
    @Transactional
    public OrganizationResponse updateOrganization(UUID orgId, UpdateOrganizationRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "orgId", orgId));

        if (StringUtils.hasText(request.getDomain()) && !request.getDomain().equalsIgnoreCase(org.getDomain())) {
            if (organizationRepository.existsByDomain(request.getDomain())) {
                throw new BadRequestException("Domain already in use by another organization");
            }
        }

        organizationMapper.updateEntityFromRequest(request, org);
        Organization updated = organizationRepository.save(org);

        auditLogService.recordEvent(
                updated.getOrgId(),
                null,
                AuditAction.ORGANIZATION_UPDATED,
                "ORGANIZATION",
                updated.getOrgId().toString(),
                "Organization updated: " + updated.getName()
        );

        return organizationMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> getAllOrganizations(Pageable pageable) {
        Page<Organization> page = organizationRepository.findAll(pageable);
        return PageResponse.from(page.map(organizationMapper::toResponse));
    }

    @Override
    @Transactional
    public void deleteOrganization(UUID orgId) {
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization", "orgId", orgId);
        }
        organizationRepository.deleteById(orgId);
        log.info("Organization deleted with ID: {}", orgId);
    }
}
