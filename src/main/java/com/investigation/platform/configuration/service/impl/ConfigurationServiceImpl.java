package com.investigation.platform.configuration.service.impl;

import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.audit.service.AuditLogService;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.configuration.dto.request.UpsertConfigurationRequest;
import com.investigation.platform.configuration.dto.response.ConfigurationResponse;
import com.investigation.platform.configuration.entity.Configuration;
import com.investigation.platform.configuration.mapper.ConfigurationMapper;
import com.investigation.platform.configuration.repository.ConfigurationRepository;
import com.investigation.platform.configuration.service.ConfigurationService;
import com.investigation.platform.exception.ResourceNotFoundException;
import com.investigation.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationServiceImpl implements ConfigurationService {

    private final ConfigurationRepository configurationRepository;
    private final ConfigurationMapper configurationMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public ConfigurationResponse setConfiguration(UpsertConfigurationRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();
        String key = request.getConfigKey().trim();

        Optional<Configuration> existingOpt = configurationRepository.findByOrgIdAndConfigKey(orgId, key);
        Configuration entity;
        AuditAction action;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setConfigValue(request.getConfigValue());
            action = AuditAction.CONFIGURATION_UPDATED;
        } else {
            entity = configurationMapper.toEntity(request, orgId);
            action = AuditAction.CONFIGURATION_CREATED;
        }

        Configuration saved = configurationRepository.save(entity);
        log.info("Configuration key '{}' saved for org: {}", key, orgId);

        auditLogService.recordCurrentTenantEvent(
                null,
                action,
                "CONFIGURATION",
                saved.getConfigId().toString(),
                "Configuration key: " + key
        );

        return configurationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationResponse getConfigurationByKey(String configKey) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Configuration config = configurationRepository.findByOrgIdAndConfigKey(orgId, configKey.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Configuration", "configKey", configKey));
        return configurationMapper.toResponse(config);
    }

    @Override
    @Transactional
    public void deleteConfiguration(String configKey) {
        UUID orgId = TenantContext.getRequiredTenantId();
        if (!configurationRepository.existsByOrgIdAndConfigKey(orgId, configKey.trim())) {
            throw new ResourceNotFoundException("Configuration", "configKey", configKey);
        }
        configurationRepository.deleteByOrgIdAndConfigKey(orgId, configKey.trim());
        log.info("Configuration '{}' deleted for org: {}", configKey, orgId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConfigurationResponse> getAllConfigurations(Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<Configuration> page = configurationRepository.findByOrgId(orgId, pageable);
        return PageResponse.from(page.map(configurationMapper::toResponse));
    }
}
