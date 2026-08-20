package com.investigation.platform.integration.mapper;

import com.investigation.platform.integration.dto.request.CreateIntegrationRequest;
import com.investigation.platform.integration.dto.request.UpdateIntegrationRequest;
import com.investigation.platform.integration.dto.response.IntegrationResponse;
import com.investigation.platform.integration.entity.Integration;
import com.investigation.platform.integration.enums.IntegrationStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class IntegrationMapper {

    public Integration toEntity(CreateIntegrationRequest request, UUID orgId) {
        if (request == null) {
            return null;
        }
        return Integration.builder()
                .orgId(orgId)
                .name(request.getName())
                .provider(request.getProvider())
                .apiKey(request.getApiKey())
                .status(request.getStatus() != null ? request.getStatus() : IntegrationStatus.PENDING)
                .build();
    }

    public IntegrationResponse toResponse(Integration entity) {
        if (entity == null) {
            return null;
        }
        return IntegrationResponse.builder()
                .intId(entity.getIntId())
                .orgId(entity.getOrgId())
                .name(entity.getName())
                .provider(entity.getProvider())
                .apiKeyConfigured(StringUtils.hasText(entity.getApiKey()))
                .status(entity.getStatus())
                .lastSyncAt(entity.getLastSyncAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateEntityFromRequest(UpdateIntegrationRequest request, Integration entity) {
        if (request == null || entity == null) {
            return;
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getProvider() != null) {
            entity.setProvider(request.getProvider());
        }
        if (request.getApiKey() != null) {
            entity.setApiKey(request.getApiKey());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
    }
}
