package com.investigation.platform.configuration.service;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.configuration.dto.request.UpsertConfigurationRequest;
import com.investigation.platform.configuration.dto.response.ConfigurationResponse;
import org.springframework.data.domain.Pageable;

public interface ConfigurationService {

    ConfigurationResponse setConfiguration(UpsertConfigurationRequest request);

    ConfigurationResponse getConfigurationByKey(String configKey);

    void deleteConfiguration(String configKey);

    PageResponse<ConfigurationResponse> getAllConfigurations(Pageable pageable);
}
