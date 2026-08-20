package com.investigation.platform.tenant;

import com.investigation.platform.common.constants.AppConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class HeaderTenantResolver implements TenantResolver {

    @Override
    public Optional<UUID> resolveTenantId(HttpServletRequest request) {
        String tenantHeader = request.getHeader(AppConstants.TENANT_HEADER);
        if (StringUtils.hasText(tenantHeader)) {
            try {
                return Optional.of(UUID.fromString(tenantHeader.trim()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenant ID format received in header: {}", tenantHeader);
            }
        }
        return Optional.empty();
    }
}
