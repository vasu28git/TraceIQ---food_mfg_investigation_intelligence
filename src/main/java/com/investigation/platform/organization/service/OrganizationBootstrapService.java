package com.investigation.platform.organization.service;

import java.util.UUID;

public interface OrganizationBootstrapService {

    void bootstrapOrganization(UUID orgId);

    void bootstrapOrganization(UUID orgId, String adminEmail, String adminName, String rawPassword);
}
