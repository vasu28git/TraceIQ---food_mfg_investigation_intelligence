package com.taceiq.security;

import com.taceiq.entity.Permission;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import com.taceiq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central RBAC + tenant isolation helper.
 * - Validates authenticated user belongs to target org (already done in controllers, but also provides helper)
 * - Validates permission via SecurityContext authorities (from JWT) + DB fallback (live role permissions)
 * - Supports alias-tolerant checks (e.g. USER_CREATE <-> CREATE_USER <-> MANAGE_USERS)
 * - Privilege-escalation guards for role/permission assignment
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserRepository userRepository;

    public Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        return auth;
    }

    public boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> "PLATFORM_ADMIN".equals(a.getAuthority()));
    }

    public Long getCurrentOrgId() {
        Authentication auth = getAuthentication();
        if (isPlatformAdmin()) {
            throw new AccessDeniedException("Platform Admin has no organisation scope");
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        if (user.getOrganisation() == null || user.getOrganisation().getOrgId() == null) {
            throw new AccessDeniedException("User has no organisation");
        }
        return user.getOrganisation().getOrgId();
    }

    public User getCurrentUser() {
        Authentication auth = getAuthentication();
        if (isPlatformAdmin()) {
            throw new AccessDeniedException("Platform Admin has no organisation user");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
    }

    public Set<String> getCurrentAuthorities() {
        Authentication auth = getAuthentication();
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().toUpperCase())
                .collect(Collectors.toSet());
    }

    /**
     * Case-insensitive exact check + DB fallback.
     * Also checks live DB role permissions to handle stale JWT after permission changes.
     */
    public boolean hasPermission(String required) {
        if (required == null) return false;
        String need = required.toUpperCase();
        Set<String> auths = getCurrentAuthorities();
        if (auths.contains(need)) return true;

        // DB fallback: live role permissions (handles revoked/added perms after login)
        try {
            User current = getCurrentUser();
            if (current.getRole() != null && current.getRole().getPermissions() != null) {
                for (Permission p : current.getRole().getPermissions()) {
                    if (need.equalsIgnoreCase(p.getName())) return true;
                }
            }
        } catch (Exception ignored) {
            // if DB unavailable, fall back to token only
        }
        return false;
    }

    public boolean hasAnyPermission(String... perms) {
        for (String p : perms) {
            if (hasPermission(p)) return true;
        }
        return false;
    }

    public void requireAnyPermission(String... perms) {
        if (!hasAnyPermission(perms)) {
            throw new AccessDeniedException("Missing required permission: " + String.join(" or ", perms));
        }
    }

    public void requirePermission(String perm) {
        requireAnyPermission(perm);
    }

    // --- Resource-specific helpers with alias tolerance ---

    public void requireUserCreate() {
        requireAnyPermission("USER_CREATE", "CREATE_USER", "MANAGE_USERS", "MANAGE_USER", "USER_MANAGE", "USERS_MANAGE");
    }

    public void requireUserRead() {
        requireAnyPermission("USER_READ", "READ_USER", "MANAGE_USERS", "MANAGE_USER", "USER_MANAGE", "USERS_READ", "USER_CREATE", "CREATE_USER");
    }

    public void requireUserUpdate() {
        requireAnyPermission("USER_UPDATE", "UPDATE_USER", "MANAGE_USERS", "MANAGE_USER", "USER_MANAGE", "USER_CREATE", "CREATE_USER");
    }

    public void requireUserDelete() {
        requireAnyPermission("USER_DELETE", "DELETE_USER", "MANAGE_USERS", "MANAGE_USER", "USER_MANAGE");
    }

    public void requireRoleCreate() {
        requireAnyPermission("ROLE_CREATE", "CREATE_ROLE", "MANAGE_ROLES", "MANAGE_ROLE", "ROLE_MANAGE", "ROLES_MANAGE");
    }

    public void requireRoleRead() {
        requireAnyPermission("ROLE_READ", "READ_ROLE", "MANAGE_ROLES", "MANAGE_ROLE", "ROLE_MANAGE", "ROLES_READ", "ROLE_CREATE", "CREATE_ROLE");
    }

    public void requireRoleUpdate() {
        requireAnyPermission("ROLE_UPDATE", "UPDATE_ROLE", "MANAGE_ROLES", "MANAGE_ROLE", "ROLE_MANAGE");
    }

    public void requireRoleDelete() {
        requireAnyPermission("ROLE_DELETE", "DELETE_ROLE", "MANAGE_ROLES", "MANAGE_ROLE", "ROLE_MANAGE");
    }

    public void requirePermissionAssign() {
        requireAnyPermission("PERMISSION_ASSIGN", "ASSIGN_PERMISSION", "MANAGE_ROLES", "MANAGE_ROLE", "MANAGE_PERMISSIONS", "MANAGE_PERMISSION", "PERMISSION_MANAGE", "ROLE_MANAGE");
    }

    public void requirePermissionRead() {
        requireAnyPermission("PERMISSION_READ", "READ_PERMISSION", "MANAGE_PERMISSIONS", "MANAGE_PERMISSION", "PERMISSION_MANAGE", "MANAGE_ROLES", "ROLE_READ", "READ_ROLE");
    }

    public void requireConfigCreate() {
        requireAnyPermission("CONFIG_CREATE", "CREATE_CONFIGURATION", "CREATE_CONFIG", "MANAGE_CONFIGURATIONS", "MANAGE_CONFIGURATION", "CONFIGURATION_MANAGE", "CONFIG_MANAGE");
    }

    public void requireConfigRead() {
        requireAnyPermission("CONFIG_READ", "READ_CONFIGURATION", "READ_CONFIG", "MANAGE_CONFIGURATIONS", "MANAGE_CONFIGURATION", "CONFIGURATION_READ", "CONFIG_CREATE", "CREATE_CONFIGURATION");
    }

    public void requireConfigUpdate() {
        requireAnyPermission("CONFIG_UPDATE", "UPDATE_CONFIGURATION", "UPDATE_CONFIG", "MANAGE_CONFIGURATIONS", "MANAGE_CONFIGURATION", "CONFIGURATION_MANAGE");
    }

    public void requireConfigDelete() {
        requireAnyPermission("CONFIG_DELETE", "DELETE_CONFIGURATION", "DELETE_CONFIG", "MANAGE_CONFIGURATIONS", "MANAGE_CONFIGURATION");
    }

    public void requireIntegrationCreate() {
        requireAnyPermission("INTEGRATION_CREATE", "CREATE_INTEGRATION", "MANAGE_INTEGRATIONS", "MANAGE_INTEGRATION", "INTEGRATION_MANAGE");
    }

    public void requireIntegrationRead() {
        requireAnyPermission("INTEGRATION_READ", "READ_INTEGRATION", "MANAGE_INTEGRATIONS", "MANAGE_INTEGRATION", "INTEGRATION_MANAGE", "INTEGRATION_CREATE", "CREATE_INTEGRATION");
    }

    public void requireIntegrationUpdate() {
        requireAnyPermission("INTEGRATION_UPDATE", "UPDATE_INTEGRATION", "MANAGE_INTEGRATIONS", "MANAGE_INTEGRATION", "INTEGRATION_MANAGE");
    }

    public void requireIntegrationDelete() {
        requireAnyPermission("INTEGRATION_DELETE", "DELETE_INTEGRATION", "MANAGE_INTEGRATIONS", "MANAGE_INTEGRATION");
    }

    public void requireFileCreate() {
        requireAnyPermission("FILE_CREATE", "CREATE_FILE", "MANAGE_FILES", "MANAGE_FILE", "FILE_MANAGE");
    }

    public void requireFileRead() {
        requireAnyPermission("FILE_READ", "READ_FILE", "MANAGE_FILES", "MANAGE_FILE", "FILE_MANAGE", "FILE_CREATE", "CREATE_FILE");
    }

    public void requireFileUpdate() {
        requireAnyPermission("FILE_UPDATE", "UPDATE_FILE", "MANAGE_FILES", "MANAGE_FILE", "FILE_MANAGE");
    }

    public void requireFileDelete() {
        requireAnyPermission("FILE_DELETE", "DELETE_FILE", "MANAGE_FILES", "MANAGE_FILE", "FILE_MANAGE");
    }

    public void requireEvidenceGraphAccess() {
        requireAnyPermission("EVIDENCE_GRAPH_ACCESS", "EVIDENCE_ACCESS", "INVESTIGATION_ACCESS", "MANAGE_INVESTIGATIONS", "INVESTIGATION_ADMIN");
    }

    public void requireTraceabilityAccess() {
        requireAnyPermission("TRACEABILITY_ACCESS", "EVIDENCE_GRAPH_ACCESS", "EVIDENCE_ACCESS", "INVESTIGATION_ACCESS", "INVESTIGATION_ADMIN");
    }

    // --- Privilege escalation guards ---

    /**
     * Ensures current user can assign the target role (role's permissions are subset of current user's permissions).
     * PLATFORM_ADMIN is blocked earlier. ADMIN role with all perms will always pass.
     */
    public void requireCanAssignRole(Role targetRole) {
        if (targetRole == null || targetRole.getPermissions() == null || targetRole.getPermissions().isEmpty()) {
            return; // role with no perms is always assignable if user has USER_CREATE
        }
        Set<String> currentPerms = getEffectivePermissions();
        for (Permission p : targetRole.getPermissions()) {
            String name = p.getName().toUpperCase();
            if (!currentPerms.contains(name)) {
                // also allow if current has manage variant for that resource
                // e.g., current has MANAGE_USERS but target has CREATE_USER -> allow if MANAGE covers it
                // For strictness, we require exact permission; manage check via hasAnyPermission aliases already covers many
                // So we do alias-tolerant subset check: if current has any alias that maps to p, allow
                // Simplify: if current has MANAGE_* for resource of p, allow
                if (!isCoveredByManage(currentPerms, name)) {
                    throw new AccessDeniedException("Cannot assign role with permission '" + p.getName() + "' you do not have");
                }
            }
        }
    }

    public void requireCanAssignPermissions(Collection<Permission> permsToAssign) {
        if (permsToAssign == null || permsToAssign.isEmpty()) return;
        Set<String> currentPerms = getEffectivePermissions();
        for (Permission p : permsToAssign) {
            String name = p.getName().toUpperCase();
            if (!currentPerms.contains(name) && !isCoveredByManage(currentPerms, name)) {
                throw new AccessDeniedException("Cannot grant permission '" + p.getName() + "' you do not have");
            }
        }
    }

    private Set<String> getEffectivePermissions() {
        Set<String> perms = getCurrentAuthorities();
        // also merge live DB perms
        try {
            User current = getCurrentUser();
            if (current.getRole() != null && current.getRole().getPermissions() != null) {
                for (Permission p : current.getRole().getPermissions()) {
                    perms.add(p.getName().toUpperCase());
                }
            }
        } catch (Exception ignored) {}
        return perms;
    }

    private boolean isCoveredByManage(Set<String> currentPerms, String targetPerm) {
        // e.g., target CREATE_USER is covered if current has MANAGE_USERS
        String upper = targetPerm.toUpperCase();
        // derive resource: split by _ and assume last token is resource or first is verb
        // For MANAGE_* covering, check if current has MANAGE_* for same resource
        for (String cp : currentPerms) {
            if (cp.startsWith("MANAGE_")) {
                String managedResource = cp.substring("MANAGE_".length()); // e.g., USERS
                // target contains resource?
                if (upper.contains(managedResource) || managedResource.contains(upper.replace("CREATE_", "").replace("READ_", "").replace("UPDATE_", "").replace("DELETE_", ""))) {
                    return true;
                }
                // For file/config/integration etc., simple contains check
                if (upper.contains(managedResource.replaceAll("S$", ""))) return true;
            }
        }
        return false;
    }
}
