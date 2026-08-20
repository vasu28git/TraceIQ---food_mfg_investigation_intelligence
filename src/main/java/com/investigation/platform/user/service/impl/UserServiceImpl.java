package com.investigation.platform.user.service.impl;

import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.audit.service.AuditLogService;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.exception.BadRequestException;
import com.investigation.platform.exception.ResourceNotFoundException;
import com.investigation.platform.role.repository.RoleRepository;
import com.investigation.platform.tenant.TenantContext;
import com.investigation.platform.user.dto.request.CreateUserRequest;
import com.investigation.platform.user.dto.request.UpdateUserRequest;
import com.investigation.platform.user.dto.response.UserResponse;
import com.investigation.platform.user.entity.User;
import com.investigation.platform.user.enums.UserStatus;
import com.investigation.platform.user.mapper.UserMapper;
import com.investigation.platform.user.repository.UserRepository;
import com.investigation.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();

        if (userRepository.existsByEmailAndOrgId(request.getEmail().toLowerCase().trim(), orgId)) {
            throw new BadRequestException("User with email '" + request.getEmail() + "' already exists in this organization");
        }

        if (!roleRepository.existsByRoleIdAndOrgId(request.getRoleId(), orgId)) {
            throw new BadRequestException("Role not found in this organization: " + request.getRoleId());
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = userMapper.toEntity(request, orgId, encodedPassword);
        User savedUser = userRepository.save(user);

        log.info("User created: {} in organization: {}", savedUser.getEmail(), orgId);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.USER_CREATED,
                "USER",
                savedUser.getUserId().toString(),
                "User created: " + savedUser.getEmail()
        );

        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        User user = userRepository.findByUserIdAndOrgId(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();
        User user = userRepository.findByUserIdAndOrgId(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailAndOrgId(request.getEmail().toLowerCase().trim(), orgId)) {
                throw new BadRequestException("Email already in use by another user in this organization");
            }
        }

        if (request.getRoleId() != null && !request.getRoleId().equals(user.getRoleId())) {
            if (!roleRepository.existsByRoleIdAndOrgId(request.getRoleId(), orgId)) {
                throw new BadRequestException("Role not found in this organization: " + request.getRoleId());
            }
        }

        userMapper.updateEntityFromRequest(request, user);
        User updated = userRepository.save(user);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.USER_UPDATED,
                "USER",
                updated.getUserId().toString(),
                "User updated: " + updated.getEmail()
        );

        return userMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        if (!userRepository.existsByUserIdAndOrgId(userId, orgId)) {
            throw new ResourceNotFoundException("User", "userId", userId);
        }
        userRepository.deleteByUserIdAndOrgId(userId, orgId);
        log.info("User deleted: {} in org: {}", userId, orgId);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.USER_DELETED,
                "USER",
                userId.toString(),
                "User deleted"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<User> page = userRepository.findByOrgId(orgId, pageable);
        return PageResponse.from(page.map(userMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsersByStatus(UserStatus status, Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<User> page = userRepository.findByOrgIdAndStatus(orgId, status, pageable);
        return PageResponse.from(page.map(userMapper::toResponse));
    }
}
