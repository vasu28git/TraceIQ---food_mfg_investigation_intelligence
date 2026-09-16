package com.taceiq.controller;

import com.taceiq.dto.UserCreateRequest;
import com.taceiq.dto.UserResponse;
import com.taceiq.dto.UserUpdateRequest;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthorizationService authorizationService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody Map<String, Object> payload) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireUserCreate();
        // Support both UserCreateRequest (roleId) and legacy User (role: {id:}) payloads
        User user;
        if (payload.containsKey("roleId")) {
            UserCreateRequest req = new UserCreateRequest();
            req.setUsername((String) payload.get("username"));
            req.setPassword((String) payload.get("password"));
            Object roleIdObj = payload.get("roleId");
            if (roleIdObj != null) req.setRoleId(Long.valueOf(roleIdObj.toString()));
            req.setStatus((String) payload.get("status"));
            user = userService.createUserFromRequest(req, orgId);
        } else {
            // Legacy User entity path (includes role object)
            // Map payload to User via Jackson-like manual
            user = new User();
            user.setUsername((String) payload.get("username"));
            user.setPassword((String) payload.get("password"));
            user.setStatus((String) payload.get("status"));
            Object roleObj = payload.get("role");
            if (roleObj instanceof Map) {
                Map<?,?> roleMap = (Map<?,?>) roleObj;
                Object idObj = roleMap.get("id");
                if (idObj != null) {
                    Role r = new Role();
                    r.setId(Long.valueOf(idObj.toString()));
                    user.setRole(r);
                }
            }
            // Also handle direct roleId in payload without wrapper
            if (payload.get("roleId") != null && user.getRole() == null) {
                Role r = new Role();
                r.setId(Long.valueOf(payload.get("roleId").toString()));
                user.setRole(r);
            }
            user = userService.createUser(user, orgId);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromEntity(user));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireUserRead();
        List<User> users = userService.listUsers(orgId);
        List<UserResponse> resp = users.stream().map(UserResponse::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireUserRead();
        User user = userService.getUserById(id, orgId);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireUserRead();
        User user = userService.getUserByUsername(username, orgId);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @GetMapping("/org/{orgId}")
    public ResponseEntity<List<UserResponse>> getUsersByOrg(@PathVariable Long orgId) {
        Long currentOrgId = authorizationService.getCurrentOrgId();
        authorizationService.requireUserRead();
        List<User> users = userService.getUsersByOrg(orgId, currentOrgId);
        List<UserResponse> resp = users.stream().map(UserResponse::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireUserUpdate();
        User updated = userService.updateUser(id, orgId, request);
        return ResponseEntity.ok(UserResponse.fromEntity(updated));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireUserUpdate();
        String status = body.get("status");
        User updated = userService.updateUserStatus(id, orgId, status);
        return ResponseEntity.ok(UserResponse.fromEntity(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireUserDelete();
        userService.deleteUser(id, orgId);
        return ResponseEntity.noContent().build();
    }
}
