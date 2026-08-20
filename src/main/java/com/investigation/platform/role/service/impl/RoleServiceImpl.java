package com.investigation.platform.role.service.impl;

import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.audit.service.AuditLogService;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.exception.BadRequestException;
import com.investigation.platform.exception.ResourceNotFoundException;
import com.investigation.platform.permission.entity.Permission;
import com.investigation.platform.permission.repository.PermissionRepository;
import com.investigation.platform.role.dto.request.AssignPermissionsRequest;
import com.investigation.platform.role.dto.request.CreateRoleRequest;
import com.investigation.platform.role.dto.request.UpdateRoleRequest;
import com.investigation.platform.role.dto.response.RoleResponse;
import com.investigation.platform.role.entity.Role;
import com.investigation.platform.role.mapper.RoleMapper;
import com.investigation.platform.role.repository.RoleRepository;
import com.investigation.platform.role.service.RoleService;
import com.investigation.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();
        if (roleRepository.existsByNameAndOrgId(request.getName(), orgId)) {
            throw new BadRequestException("Role with name '" + request.getName() + "' already exists in this organization");
        }

        Role role = roleMapper.toEntity(request, orgId);

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            role.setPermissions(permissions);
        }

        Role savedRole = roleRepository.save(role);
        log.info("Role created: {} for organization: {}", savedRole.getName(), orgId);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.ROLE_CREATED,
                "ROLE",
                savedRole.getRoleId().toString(),
                "Role created: " + savedRole.getName()
        );

        return roleMapper.toResponse(savedRole);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(UUID roleId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Role role = roleRepository.findByIdWithPermissions(roleId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "roleId", roleId));
        return roleMapper.toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(UUID roleId, UpdateRoleRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Role role = roleRepository.findByRoleIdAndOrgId(roleId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "roleId", roleId));

        if (request.getName() != null && !request.getName().equalsIgnoreCase(role.getName())) {
            if (roleRepository.existsByNameAndOrgId(request.getName(), orgId)) {
                throw new BadRequestException("Role with name '" + request.getName() + "' already exists");
            }
        }

        roleMapper.updateEntityFromRequest(request, role);

        if (request.getPermissionIds() != null) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            role.setPermissions(permissions);
        }

        Role updated = roleRepository.save(role);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.ROLE_UPDATED,
                "ROLE",
                updated.getRoleId().toString(),
                "Role updated: " + updated.getName()
        );

        return roleMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public RoleResponse assignPermissions(UUID roleId, AssignPermissionsRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Role role = roleRepository.findByRoleIdAndOrgId(roleId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "roleId", roleId));

        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
        role.setPermissions(permissions);
        Role updated = roleRepository.save(role);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.PERMISSION_CHANGED,
                "ROLE",
                updated.getRoleId().toString(),
                "Permissions updated for role: " + updated.getName()
        );

        return roleMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteRole(UUID roleId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        if (!roleRepository.existsByRoleIdAndOrgId(roleId, orgId)) {
            throw new ResourceNotFoundException("Role", "roleId", roleId);
        }
        roleRepository.deleteByRoleIdAndOrgId(roleId, orgId);
        log.info("Role deleted: {} in org: {}", roleId, orgId);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.ROLE_DELETED,
                "ROLE",
                roleId.toString(),
                "Role deleted"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> getAllRoles(Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<Role> page = roleRepository.findByOrgId(orgId, pageable);
        return PageResponse.from(page.map(roleMapper::toResponse));
    }
}
