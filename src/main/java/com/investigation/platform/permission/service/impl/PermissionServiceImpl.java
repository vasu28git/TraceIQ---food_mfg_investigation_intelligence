package com.investigation.platform.permission.service.impl;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.exception.ResourceNotFoundException;
import com.investigation.platform.permission.dto.response.PermissionResponse;
import com.investigation.platform.permission.entity.Permission;
import com.investigation.platform.permission.mapper.PermissionMapper;
import com.investigation.platform.permission.repository.PermissionRepository;
import com.investigation.platform.permission.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(permissionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> getPermissionsPage(Pageable pageable) {
        Page<Permission> page = permissionRepository.findAll(pageable);
        return PageResponse.from(page.map(permissionMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getPermissionById(UUID permId) {
        Permission permission = permissionRepository.findById(permId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "permId", permId));
        return permissionMapper.toResponse(permission);
    }
}
