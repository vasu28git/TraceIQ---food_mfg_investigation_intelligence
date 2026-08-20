package com.investigation.platform.user.dto.response;

import com.investigation.platform.role.dto.response.RoleResponse;
import com.investigation.platform.user.enums.UserStatus;
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
public class UserResponse {

    private UUID userId;
    private UUID orgId;
    private UUID roleId;
    private RoleResponse role;
    private String name;
    private String email;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
