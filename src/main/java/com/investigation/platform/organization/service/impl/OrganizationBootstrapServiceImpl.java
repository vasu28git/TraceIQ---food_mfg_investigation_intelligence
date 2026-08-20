package com.investigation.platform.organization.service.impl;

import com.investigation.platform.organization.service.OrganizationBootstrapService;
import com.investigation.platform.permission.entity.Permission;
import com.investigation.platform.permission.repository.PermissionRepository;
import com.investigation.platform.role.entity.Role;
import com.investigation.platform.role.repository.RoleRepository;
import com.investigation.platform.user.entity.User;
import com.investigation.platform.user.enums.UserStatus;
import com.investigation.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationBootstrapServiceImpl implements OrganizationBootstrapService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void bootstrapOrganization(UUID orgId) {
        // Structural placeholder for full onboarding orchestration:
        // Create Organization -> Create Default Roles -> Create Permissions -> Create Org Admin -> Send Invitation
        log.info("Executing bootstrap organization placeholder for orgId: {}", orgId);
        bootstrapOrganization(orgId, "admin@" + orgId + ".local", "Organization Administrator", "Admin@123456");
    }

    @Override
    @Transactional
    public void bootstrapOrganization(UUID orgId, String adminEmail, String adminName, String rawPassword) {
        log.info("Bootstrapping default roles and admin user for organization: {}", orgId);

        List<Permission> allPermissions = permissionRepository.findAll();
        Set<Permission> adminPerms = new HashSet<>(allPermissions);

        // 1. Create Default Role: ORG_ADMIN
        Role adminRole = Role.builder()
                .orgId(orgId)
                .name("ORG_ADMIN")
                .description("Organization Administrator with full tenant privileges")
                .permissions(adminPerms)
                .build();
        Role savedAdminRole = roleRepository.save(adminRole);

        // 2. Create Default Role: INVESTIGATOR
        Set<Permission> investigatorPerms = new HashSet<>();
        for (Permission perm : allPermissions) {
            String name = perm.getName();
            if (name.startsWith("FILE_") || name.startsWith("INTEGRATION_READ") || name.startsWith("USER_READ") || name.startsWith("ROLE_READ")) {
                investigatorPerms.add(perm);
            }
        }

        Role investigatorRole = Role.builder()
                .orgId(orgId)
                .name("INVESTIGATOR")
                .description("Investigator with access to cases, evidence, and analytics")
                .permissions(investigatorPerms)
                .build();
        roleRepository.save(investigatorRole);

        // 3. Create Default Org Admin User
        User adminUser = User.builder()
                .orgId(orgId)
                .roleId(savedAdminRole.getRoleId())
                .name(adminName != null ? adminName : "Organization Administrator")
                .email(adminEmail.toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(adminUser);

        log.info("Organization {} bootstrap complete. Admin: {}", orgId, adminEmail);
    }
}
