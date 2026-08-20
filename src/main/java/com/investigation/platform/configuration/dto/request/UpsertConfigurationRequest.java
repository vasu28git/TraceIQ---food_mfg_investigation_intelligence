package com.investigation.platform.configuration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertConfigurationRequest {

    @NotBlank(message = "Configuration key is required")
    @Size(max = 100, message = "Key must not exceed 100 characters")
    private String configKey;

    @NotBlank(message = "Configuration value is required")
    private String configValue;
}
