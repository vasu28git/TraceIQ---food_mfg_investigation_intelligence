package com.investigation.platform.role.dto.response;

import com.investigation.platform.permission.dto.response.PermissionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private UUID roleId;
    private UUID orgId;
    private String name;
    private String description;
    private Set<PermissionResponse> permissions;
    private Instant createdAt;
    private Instant updatedAt;
}
