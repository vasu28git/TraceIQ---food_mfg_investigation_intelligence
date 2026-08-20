package com.investigation.platform.integration.dto.request;

import com.investigation.platform.integration.enums.IntegrationStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIntegrationRequest {

    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @Size(max = 100, message = "Provider must not exceed 100 characters")
    private String provider;

    private String apiKey;

    private IntegrationStatus status;
}
