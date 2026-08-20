package com.investigation.platform.permission.service;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.permission.dto.response.PermissionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PermissionService {

    List<PermissionResponse> getAllPermissions();

    PageResponse<PermissionResponse> getPermissionsPage(Pageable pageable);

    PermissionResponse getPermissionById(UUID permId);
}
