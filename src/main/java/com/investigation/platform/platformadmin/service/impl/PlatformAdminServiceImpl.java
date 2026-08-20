package com.investigation.platform.platformadmin.service.impl;

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
import com.investigation.platform.platformadmin.dto.request.UpdateOrganizationStatusRequest;
import com.investigation.platform.platformadmin.service.PlatformAdminService;
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
public class PlatformAdminServiceImpl implements PlatformAdminService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;
    private final OrganizationBootstrapService organizationBootstrapService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        log.info("Platform Admin initiating organization creation: {}", request.getName());

        if (StringUtils.hasText(request.getDomain()) && organizationRepository.existsByDomain(request.getDomain())) {
            throw new BadRequestException("Organization domain already exists: " + request.getDomain());
        }

        Organization org = organizationMapper.toEntity(request);
        Organization savedOrg = organizationRepository.save(org);

        // Bootstrap organization via bootstrap service structure
        organizationBootstrapService.bootstrapOrganization(savedOrg.getOrgId());

        auditLogService.recordEvent(
                savedOrg.getOrgId(),
                null,
                AuditAction.ORGANIZATION_CREATED,
                "ORGANIZATION",
                savedOrg.getOrgId().toString(),
                "Platform Admin created organization: " + savedOrg.getName()
        );

        return organizationMapper.toResponse(savedOrg);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> getAllOrganizations(Pageable pageable) {
        log.debug("Platform Admin fetching all organizations");
        Page<Organization> page = organizationRepository.findAll(pageable);
        return PageResponse.from(page.map(organizationMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(UUID id) {
        log.debug("Platform Admin fetching organization by ID: {}", id);
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "orgId", id));
        return organizationMapper.toResponse(org);
    }

    @Override
    @Transactional
    public OrganizationResponse updateOrganization(UUID id, UpdateOrganizationRequest request) {
        log.info("Platform Admin updating organization: {}", id);
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "orgId", id));

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
                "Platform Admin updated organization: " + updated.getName()
        );

        return organizationMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public OrganizationResponse updateOrganizationStatus(UUID id, UpdateOrganizationStatusRequest request) {
        log.info("Platform Admin updating organization {} status to: {}", id, request.getStatus());
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "orgId", id));

        org.setStatus(request.getStatus());
        Organization updated = organizationRepository.save(org);

        auditLogService.recordEvent(
                updated.getOrgId(),
                null,
                AuditAction.ORGANIZATION_STATUS_CHANGED,
                "ORGANIZATION",
                updated.getOrgId().toString(),
                "Platform Admin changed organization status to: " + request.getStatus()
        );

        return organizationMapper.toResponse(updated);
    }
}
