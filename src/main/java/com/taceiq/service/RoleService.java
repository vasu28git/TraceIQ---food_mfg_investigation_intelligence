package com.taceiq.service;

import com.taceiq.entity.Permission;
import com.taceiq.entity.Role;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.repository.PermissionRepository;
import com.taceiq.repository.RoleRepository;
import com.taceiq.repository.UserRepository;
import com.taceiq.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final OrganisationRepository organisationRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    public Role createRole(Role role) {
        Long orgId = role.getOrganisation() != null ? role.getOrganisation().getOrgId() : null;
        if (roleRepository.existsByNameAndOrganisationOrgId(role.getName(), orgId)) {
            throw new RuntimeException("Role already exists with name: " + role.getName() + " for orgId: " + orgId);
        }
        return roleRepository.save(role);
    }

    public Role createRole(Role role, Long currentOrgId) {
        if (role.getName() == null || role.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name is required");
        }
        String trimmedName = role.getName().trim();
        if (roleRepository.existsByNameAndOrganisationOrgId(trimmedName, currentOrgId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists with name: " + trimmedName + " for orgId: " + currentOrgId);
        }
        // Force organisation to current user's org, ignore client value
        role.setName(trimmedName);
        role.setOrganisation(organisationRepository.getReferenceById(currentOrgId));
        // Do not allow client to set permissions directly via create - permissions must be assigned via dedicated endpoint with privilege checks
        if (role.getPermissions() != null) {
            role.setPermissions(new HashSet<>());
        }
        return roleRepository.save(role);
    }

    public Role getRoleById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + id));
    }

    public Role getRoleById(Long id, Long currentOrgId) {
        return roleRepository.findByIdAndOrganisationOrgId(id, currentOrgId)
                .orElseThrow(() -> new AccessDeniedException("Role not found or not in your organisation: " + id));
    }

    public Role getRoleByNameAndOrgId(String name, Long orgId) {
        return roleRepository.findByNameAndOrganisationOrgId(name, orgId)
                .orElseThrow(() -> new RuntimeException("Role not found with name: " + name + " and orgId: " + orgId));
    }

    public Role getRoleByNameAndOrgId(String name, Long requestedOrgId, Long currentOrgId) {
        if (!requestedOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Cannot access roles of another organisation");
        }
        return getRoleByNameAndOrgId(name, currentOrgId);
    }

    public List<Role> getRolesByOrgId(Long orgId) {
        return roleRepository.findByOrganisationOrgId(orgId);
    }

    public List<Role> getRolesByOrgId(Long requestedOrgId, Long currentOrgId) {
        if (!requestedOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Cannot access roles of another organisation");
        }
        return roleRepository.findByOrganisationOrgId(currentOrgId);
    }

    public Role updateRole(Long id, Role updated) {
        Role existing = getRoleById(id);
        existing.setName(updated.getName());
        existing.setOrganisation(updated.getOrganisation());
        existing.setDescription(updated.getDescription());
        return roleRepository.save(existing);
    }

    @Transactional
    public Role updateRole(Long id, Role updated, Long currentOrgId) {
        Role existing = getRoleById(id, currentOrgId);
        if (updated.getName() != null) {
            String newName = updated.getName().trim();
            if (newName.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name cannot be blank");
            }
            if (!newName.equalsIgnoreCase(existing.getName())) {
                if (roleRepository.existsByNameAndOrganisationOrgId(newName, currentOrgId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists with name: " + newName + " for orgId: " + currentOrgId);
                }
                existing.setName(newName);
            }
        }
        // Never allow organisation change via update
        // Never allow permissions change via update (use dedicated endpoints)
        if (updated.getDescription() != null) {
            existing.setDescription(updated.getDescription());
        }
        return roleRepository.save(existing);
    }

    public void deleteRole(Long id) {
        Role existing = getRoleById(id);
        roleRepository.delete(existing);
    }

    @Transactional
    public void deleteRole(Long id, Long currentOrgId) {
        Role existing = getRoleById(id, currentOrgId);
        // Protected ADMIN role cannot be deleted
        if ("ADMIN".equalsIgnoreCase(existing.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete protected ADMIN role");
        }
        // Prevent unsafe deletion if role is assigned to users
        List<com.taceiq.entity.User> assignedUsers = userRepository.findByRoleIdAndOrganisationOrgId(existing.getId(), currentOrgId);
        if (!assignedUsers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete role assigned to users (" + assignedUsers.size() + " user(s) still assigned)");
        }
        roleRepository.delete(existing);
    }

    public Role assignPermissionsToRole(Long roleId, Set<Long> permissionIds) {
        Role role = getRoleById(roleId);
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        role.getPermissions().addAll(permissions);
        return roleRepository.save(role);
    }

    @Transactional
    public Role assignPermissionsToRole(Long roleId, Set<Long> permissionIds, Long currentOrgId) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission IDs are required");
        }
        Role role = roleRepository.findWithPermissionsByIdAndOrganisationOrgId(roleId, currentOrgId)
                .orElseThrow(() -> new AccessDeniedException("Role not found or not in your organisation: " + roleId));
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        if (permissions.size() != permissionIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more permissions not found");
        }
        // privilege escalation: cannot grant permissions you don't have
        authorizationService.requireCanAssignPermissions(permissions);
        // Prevent duplicate assignments - only add new ones
        Set<Long> existingIds = new HashSet<>();
        for (Permission p : role.getPermissions()) {
            existingIds.add(p.getId());
        }
        Set<Permission> toAdd = new HashSet<>();
        for (Permission p : permissions) {
            if (!existingIds.contains(p.getId())) {
                toAdd.add(p);
            }
        }
        if (toAdd.isEmpty()) {
            return role; // idempotent - already assigned
        }
        role.getPermissions().addAll(toAdd);
        return roleRepository.save(role);
    }

    public Role removePermissionsFromRole(Long roleId, Set<Long> permissionIds) {
        Role role = getRoleById(roleId);
        role.getPermissions().removeIf(p -> permissionIds.contains(p.getId()));
        return roleRepository.save(role);
    }

    public Role removePermissionsFromRole(Long roleId, Set<Long> permissionIds, Long currentOrgId) {
        Role role = roleRepository.findWithPermissionsByIdAndOrganisationOrgId(roleId, currentOrgId)
                .orElseThrow(() -> new AccessDeniedException("Role not found or not in your organisation: " + roleId));
        role.getPermissions().removeIf(p -> permissionIds.contains(p.getId()));
        return roleRepository.save(role);
    }

    public Set<Permission> getPermissionsForRole(Long roleId) {
        Role role = getRoleById(roleId);
        return role.getPermissions();
    }

    public Set<Permission> getPermissionsForRole(Long roleId, Long currentOrgId) {
        Role role = roleRepository.findWithPermissionsByIdAndOrganisationOrgId(roleId, currentOrgId)
                .orElseThrow(() -> new AccessDeniedException("Role not found or not in your organisation: " + roleId));
        return role.getPermissions();
    }
}
