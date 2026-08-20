package com.investigation.platform.integration.service;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.integration.dto.request.CreateIntegrationRequest;
import com.investigation.platform.integration.dto.request.UpdateIntegrationRequest;
import com.investigation.platform.integration.dto.response.IntegrationResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IntegrationService {

    IntegrationResponse createIntegration(CreateIntegrationRequest request);

    IntegrationResponse getIntegrationById(UUID intId);

    IntegrationResponse updateIntegration(UUID intId, UpdateIntegrationRequest request);

    void deleteIntegration(UUID intId);

    PageResponse<IntegrationResponse> getAllIntegrations(Pageable pageable);

    IntegrationResponse triggerSync(UUID intId);
}
