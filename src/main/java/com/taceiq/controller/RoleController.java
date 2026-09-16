package com.taceiq.controller;

import com.taceiq.entity.Permission;
import com.taceiq.entity.Role;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final AuthorizationService authorizationService;

    @PostMapping
    public ResponseEntity<Role> createRole(@RequestBody Role role) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireRoleCreate();
        Role created = roleService.createRole(role, orgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Role> getRoleById(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireRoleRead();
        Role role = roleService.getRoleById(id, orgId);
        return ResponseEntity.ok(role);
    }

    @GetMapping("/by-name")
    public ResponseEntity<Role> getRoleByNameAndOrgId(@RequestParam String name, @RequestParam Long orgId) {
        Long currentOrgId = authorizationService.getCurrentOrgId();
        authorizationService.requireRoleRead();
        Role role = roleService.getRoleByNameAndOrgId(name, orgId, currentOrgId);
        return ResponseEntity.ok(role);
    }

    @GetMapping("/org/{orgId}")
    public ResponseEntity<List<Role>> getRolesByOrgId(@PathVariable Long orgId) {
        Long currentOrgId = authorizationService.getCurrentOrgId();
        authorizationService.requireRoleRead();
        List<Role> roles = roleService.getRolesByOrgId(orgId, currentOrgId);
        return ResponseEntity.ok(roles);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireRoleUpdate();
        Role updated = roleService.updateRole(id, role, orgId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireRoleDelete();
        roleService.deleteRole(id, orgId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<Role> assignPermissionsToRole(@PathVariable Long id, @RequestBody Set<Long> permissionIds) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requirePermissionAssign();
        Role updated = roleService.assignPermissionsToRole(id, permissionIds, orgId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/permissions")
    public ResponseEntity<Role> removePermissionsFromRole(@PathVariable Long id, @RequestBody Set<Long> permissionIds) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requirePermissionAssign();
        Role updated = roleService.removePermissionsFromRole(id, permissionIds, orgId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<Set<Permission>> getPermissionsForRole(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requirePermissionRead();
        Set<Permission> permissions = roleService.getPermissionsForRole(id, orgId);
        return ResponseEntity.ok(permissions);
    }
}
