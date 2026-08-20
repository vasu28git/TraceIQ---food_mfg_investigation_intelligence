package com.investigation.platform.configuration.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationResponse {

    private UUID configId;
    private UUID orgId;
    private String configKey;
    private String configValue;
    private Instant createdAt;
    private Instant updatedAt;
}
