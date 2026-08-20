package com.investigation.platform.configuration.mapper;

import com.investigation.platform.configuration.dto.request.UpsertConfigurationRequest;
import com.investigation.platform.configuration.dto.response.ConfigurationResponse;
import com.investigation.platform.configuration.entity.Configuration;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConfigurationMapper {

    public Configuration toEntity(UpsertConfigurationRequest request, UUID orgId) {
        if (request == null) {
            return null;
        }
        return Configuration.builder()
                .orgId(orgId)
                .configKey(request.getConfigKey().trim())
                .configValue(request.getConfigValue())
                .build();
    }

    public ConfigurationResponse toResponse(Configuration entity) {
        if (entity == null) {
            return null;
        }
        return ConfigurationResponse.builder()
                .configId(entity.getConfigId())
                .orgId(entity.getOrgId())
                .configKey(entity.getConfigKey())
                .configValue(entity.getConfigValue())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
