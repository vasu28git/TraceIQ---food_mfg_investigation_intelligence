package com.investigation.platform.role.service;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.role.dto.request.AssignPermissionsRequest;
import com.investigation.platform.role.dto.request.CreateRoleRequest;
import com.investigation.platform.role.dto.request.UpdateRoleRequest;
import com.investigation.platform.role.dto.response.RoleResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RoleService {

    RoleResponse createRole(CreateRoleRequest request);

    RoleResponse getRoleById(UUID roleId);

    RoleResponse updateRole(UUID roleId, UpdateRoleRequest request);

    RoleResponse assignPermissions(UUID roleId, AssignPermissionsRequest request);

    void deleteRole(UUID roleId);

    PageResponse<RoleResponse> getAllRoles(Pageable pageable);
}
