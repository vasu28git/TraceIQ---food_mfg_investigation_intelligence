package com.taceiq.controller;

import com.taceiq.entity.Permission;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final AuthorizationService authorizationService;

    @GetMapping("/{id}")
    public ResponseEntity<Permission> getPermissionById(@PathVariable Long id) {
        // org users need permission read; platform admin already blocked via org check? permissions are global, but we still enforce RBAC for org users
        // Allow platform admin to read? No, platform admin has no org scope but permissions are global catalog — allow if PLATFORM_ADMIN else require permission
        if (!authorizationService.isPlatformAdmin()) {
            authorizationService.getCurrentOrgId(); // validates org membership
            authorizationService.requirePermissionRead();
        }
        Permission permission = permissionService.getPermissionById(id);
        return ResponseEntity.ok(permission);
    }

    @GetMapping("/by-name")
    public ResponseEntity<Permission> getPermissionByName(@RequestParam String name) {
        if (!authorizationService.isPlatformAdmin()) {
            authorizationService.getCurrentOrgId();
            authorizationService.requirePermissionRead();
        }
        Permission permission = permissionService.getPermissionByName(name);
        return ResponseEntity.ok(permission);
    }

    @GetMapping
    public ResponseEntity<List<Permission>> getAllPermissions() {
        if (!authorizationService.isPlatformAdmin()) {
            authorizationService.getCurrentOrgId();
            authorizationService.requirePermissionRead();
        }
        List<Permission> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(permissions);
    }
}
