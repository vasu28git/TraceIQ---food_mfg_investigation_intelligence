package com.investigation.platform.integration.dto.response;

import com.investigation.platform.integration.enums.IntegrationStatus;
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
public class IntegrationResponse {

    private UUID intId;
    private UUID orgId;
    private String name;
    private String provider;
    private boolean apiKeyConfigured;
    private IntegrationStatus status;
    private Instant lastSyncAt;
    private Instant createdAt;
    private Instant updatedAt;
}
