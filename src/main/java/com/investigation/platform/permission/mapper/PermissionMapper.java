package com.investigation.platform.permission.mapper;

import com.investigation.platform.permission.dto.response.PermissionResponse;
import com.investigation.platform.permission.entity.Permission;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PermissionMapper {

    public PermissionResponse toResponse(Permission entity) {
        if (entity == null) {
            return null;
        }
        return PermissionResponse.builder()
                .permId(entity.getPermId())
                .name(entity.getName())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public Set<PermissionResponse> toResponseSet(Set<Permission> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptySet();
        }
        return entities.stream().map(this::toResponse).collect(Collectors.toSet());
    }
}
