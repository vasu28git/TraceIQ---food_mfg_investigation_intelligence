package com.investigation.platform.user.mapper;

import com.investigation.platform.role.mapper.RoleMapper;
import com.investigation.platform.user.dto.request.CreateUserRequest;
import com.investigation.platform.user.dto.request.UpdateUserRequest;
import com.investigation.platform.user.dto.response.UserResponse;
import com.investigation.platform.user.entity.User;
import com.investigation.platform.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final RoleMapper roleMapper;

    public User toEntity(CreateUserRequest request, UUID orgId, String encodedPassword) {
        if (request == null) {
            return null;
        }
        return User.builder()
                .orgId(orgId)
                .roleId(request.getRoleId())
                .name(request.getName())
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(encodedPassword)
                .status(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE)
                .build();
    }

    public UserResponse toResponse(User entity) {
        if (entity == null) {
            return null;
        }
        return UserResponse.builder()
                .userId(entity.getUserId())
                .orgId(entity.getOrgId())
                .roleId(entity.getRoleId())
                .role(entity.getRole() != null ? roleMapper.toResponse(entity.getRole()) : null)
                .name(entity.getName())
                .email(entity.getEmail())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateEntityFromRequest(UpdateUserRequest request, User entity) {
        if (request == null || entity == null) {
            return;
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getEmail() != null) {
            entity.setEmail(request.getEmail().toLowerCase().trim());
        }
        if (request.getRoleId() != null) {
            entity.setRoleId(request.getRoleId());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
    }
}
