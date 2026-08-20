package com.investigation.platform.integration.service.impl;

import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.audit.service.AuditLogService;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.exception.BadRequestException;
import com.investigation.platform.exception.ResourceNotFoundException;
import com.investigation.platform.integration.dto.request.CreateIntegrationRequest;
import com.investigation.platform.integration.dto.request.UpdateIntegrationRequest;
import com.investigation.platform.integration.dto.response.IntegrationResponse;
import com.investigation.platform.integration.entity.Integration;
import com.investigation.platform.integration.enums.IntegrationStatus;
import com.investigation.platform.integration.mapper.IntegrationMapper;
import com.investigation.platform.integration.repository.IntegrationRepository;
import com.investigation.platform.integration.service.IntegrationService;
import com.investigation.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationServiceImpl implements IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final IntegrationMapper integrationMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public IntegrationResponse createIntegration(CreateIntegrationRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();

        if (integrationRepository.existsByNameAndOrgId(request.getName(), orgId)) {
            throw new BadRequestException("Integration with name '" + request.getName() + "' already exists");
        }

        Integration integration = integrationMapper.toEntity(request, orgId);
        Integration saved = integrationRepository.save(integration);
        log.info("Integration registered: {} for org: {}", saved.getName(), orgId);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.INTEGRATION_CREATED,
                "INTEGRATION",
                saved.getIntId().toString(),
                "Integration registered: " + saved.getName() + " (" + saved.getProvider() + ")"
        );

        return integrationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public IntegrationResponse getIntegrationById(UUID intId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Integration integration = integrationRepository.findByIntIdAndOrgId(intId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration", "intId", intId));
        return integrationMapper.toResponse(integration);
    }

    @Override
    @Transactional
    public IntegrationResponse updateIntegration(UUID intId, UpdateIntegrationRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Integration integration = integrationRepository.findByIntIdAndOrgId(intId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration", "intId", intId));

        if (request.getName() != null && !request.getName().equalsIgnoreCase(integration.getName())) {
            if (integrationRepository.existsByNameAndOrgId(request.getName(), orgId)) {
                throw new BadRequestException("Integration with name '" + request.getName() + "' already exists");
            }
        }

        integrationMapper.updateEntityFromRequest(request, integration);
        Integration updated = integrationRepository.save(integration);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.INTEGRATION_UPDATED,
                "INTEGRATION",
                updated.getIntId().toString(),
                "Integration updated: " + updated.getName()
        );

        return integrationMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteIntegration(UUID intId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        if (!integrationRepository.existsByIntIdAndOrgId(intId, orgId)) {
            throw new ResourceNotFoundException("Integration", "intId", intId);
        }
        integrationRepository.deleteByIntIdAndOrgId(intId, orgId);
        log.info("Integration deleted: {} in org: {}", intId, orgId);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.INTEGRATION_DELETED,
                "INTEGRATION",
                intId.toString(),
                "Integration deleted"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IntegrationResponse> getAllIntegrations(Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<Integration> page = integrationRepository.findByOrgId(orgId, pageable);
        return PageResponse.from(page.map(integrationMapper::toResponse));
    }

    @Override
    @Transactional
    public IntegrationResponse triggerSync(UUID intId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Integration integration = integrationRepository.findByIntIdAndOrgId(intId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration", "intId", intId));

        // Placeholder for future sync adapter logic (SAP / MES / LMS / WMS)
        integration.setLastSyncAt(Instant.now());
        integration.setStatus(IntegrationStatus.CONNECTED);
        Integration synced = integrationRepository.save(integration);

        log.info("Integration sync triggered for: {}", intId);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.INTEGRATION_SYNCED,
                "INTEGRATION",
                synced.getIntId().toString(),
                "Sync executed for integration: " + synced.getName()
        );

        return integrationMapper.toResponse(synced);
    }
}
