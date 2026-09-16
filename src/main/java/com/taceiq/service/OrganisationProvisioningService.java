package com.taceiq.service;

import com.taceiq.dto.OrganisationProvisioningResult;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.Permission;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.repository.PermissionRepository;
import com.taceiq.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganisationProvisioningService {

    private final OrganisationService organisationService;
    private final RoleService roleService;
    private final UserService userService;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final OrganisationRepository organisationRepository;
    private final com.taceiq.repository.UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.provisioning.initial-password}")
    private String initialPassword;

    /**
     * Orchestrates complete organisation onboarding:
     * 1. Create Organisation
     * 2. Create ADMIN Role for that Organisation
     * 3. Create OG User linked to Organisation and ADMIN Role
     * All in one transaction - rollback if any step fails.
     */
    @Transactional
    public OrganisationProvisioningResult provisionOrganisation(Organisation organisationDetails) {
        // Fast duplicate validation before heavy provisioning – return 409 quickly instead of entering long transaction
        String orgName = organisationDetails.getName() != null ? organisationDetails.getName().trim() : null;
        if (orgName != null && organisationRepository.existsByName(orgName)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Organisation already exists with name: " + orgName);
        }
        String domain = organisationDetails.getDomain() != null ? organisationDetails.getDomain().trim() : null;
        if (domain != null && !domain.isEmpty() && organisationRepository.existsByDomain(domain)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Organisation already exists with domain: " + domain);
        }
        String derivedUsername = deriveUsername(orgName);
        if (userRepository.existsByUsername(derivedUsername)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Username already exists: " + derivedUsername);
        }

        // 1. Create Organisation first
        Organisation organisation = organisationService.createOrg(organisationDetails);

        // 2. Create ADMIN Role associated with organisation
        Role adminRole = roleService.createRole(Role.builder()
                .name("ADMIN")
                .organisation(organisation)
                .description("Initial ADMIN role for organisation: " + organisation.getName())
                .build());

        // 2b. Grant OG Admin all permissions except organisation creation
        // OG Admin can manage everything within its org (users/roles/permissions) but cannot create new organisations (platform-only)
        List<Permission> allPerms = permissionRepository.findAll();
        Set<Permission> allowed = allPerms.stream()
                .filter(p -> {
                    String n = p.getName().toUpperCase();
                    return !n.equals("CREATE_ORGANISATION") && !n.equals("ORGANISATION_CREATE") && !n.equals("ORG_CREATE");
                })
                .collect(Collectors.toSet());
        if (!allowed.isEmpty()) {
            // Batch insert role_permissions directly for speed (70 rows) – avoids Hibernate's 70 individual inserts
            // Do NOT set adminRole.setPermissions here to avoid Hibernate double-insert; batch is sole writer
            List<Object[]> batchArgs = allowed.stream()
                    .map(p -> new Object[]{adminRole.getId(), p.getId()})
                    .collect(Collectors.toList());
            jdbcTemplate.batchUpdate("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?) ON CONFLICT DO NOTHING", batchArgs);
            // For response visibility, we could set permissions but that would mark entity dirty and cause duplicate insert at flush.
            // So keep adminRole.permissions empty in DB response; permissions are already persisted via batch and can be re-fetched if needed.
        }

        // 3. Create OG User associated with organisation and ADMIN Role
        String username = deriveUsername(organisation.getName());
        User ogUser = User.builder()
                .username(username)
                .password(initialPassword) // will be hashed in UserService.createUser()
                .status("ACTIVE")
                .mustChangePassword(true)
                .organisation(organisation)
                .role(adminRole)
                .build();
        ogUser = userService.createUser(ogUser);

        return OrganisationProvisioningResult.builder()
                .organisation(organisation)
                .adminRole(adminRole)
                .ogUser(ogUser)
                .build();
    }

    /**
     * Convention: organisation name lowercased, non-alphanumeric -> underscore, trimmed, + "_admin"
     * e.g. "Acme Corp" -> "acme_corp_admin", "TaceIQ" -> "taceiq_admin"
     */
    private String deriveUsername(String orgName) {
        if (orgName == null || orgName.isBlank()) {
            throw new IllegalArgumentException("Organisation name required to derive username");
        }
        String base = orgName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "org";
        }
        return base + "_admin";
    }
}
