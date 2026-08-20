package com.investigation.platform.role.mapper;

import com.investigation.platform.permission.mapper.PermissionMapper;
import com.investigation.platform.role.dto.request.CreateRoleRequest;
import com.investigation.platform.role.dto.request.UpdateRoleRequest;
import com.investigation.platform.role.dto.response.RoleResponse;
import com.investigation.platform.role.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoleMapper {

    private final PermissionMapper permissionMapper;

    public Role toEntity(CreateRoleRequest request, UUID orgId) {
        if (request == null) {
            return null;
        }
        return Role.builder()
                .orgId(orgId)
                .name(request.getName())
                .description(request.getDescription())
                .build();
    }

    public RoleResponse toResponse(Role entity) {
        if (entity == null) {
            return null;
        }
        return RoleResponse.builder()
                .roleId(entity.getRoleId())
                .orgId(entity.getOrgId())
                .name(entity.getName())
                .description(entity.getDescription())
                .permissions(permissionMapper.toResponseSet(entity.getPermissions()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateEntityFromRequest(UpdateRoleRequest request, Role entity) {
        if (request == null || entity == null) {
            return;
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
    }
}
