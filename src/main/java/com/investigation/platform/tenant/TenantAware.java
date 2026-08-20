package com.investigation.platform.tenant;

import java.util.UUID;

public interface TenantAware {

    UUID getOrgId();

    void setOrgId(UUID orgId);
}
