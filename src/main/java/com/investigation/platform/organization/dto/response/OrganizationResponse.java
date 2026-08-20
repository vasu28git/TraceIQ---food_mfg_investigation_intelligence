package com.investigation.platform.organization.dto.response;

import com.investigation.platform.organization.enums.OrganizationStatus;
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
public class OrganizationResponse {

    private UUID orgId;
    private String name;
    private String domain;
    private OrganizationStatus status;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
