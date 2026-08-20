package com.investigation.platform.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

public interface TenantResolver {

    Optional<UUID> resolveTenantId(HttpServletRequest request);
}
