package com.taceiq.config;

import com.taceiq.entity.Permission;
import com.taceiq.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures RBAC permission constants exist. Idempotent.
 * These are the permissions enforced by AuthorizationService.
 * ADMIN role gets all of them via OrganisationProvisioningService (which grants all except CREATE_ORGANISATION).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class PermissionSeeder {

    private static final String[] PERMISSIONS = {
            // User
            "USER_CREATE", "CREATE_USER", "USER_READ", "READ_USER", "USER_UPDATE", "UPDATE_USER", "USER_DELETE", "DELETE_USER", "MANAGE_USERS", "MANAGE_USER", "USER_MANAGE",
            // Role
            "ROLE_CREATE", "CREATE_ROLE", "ROLE_READ", "READ_ROLE", "ROLE_UPDATE", "UPDATE_ROLE", "ROLE_DELETE", "DELETE_ROLE", "MANAGE_ROLES", "MANAGE_ROLE",
            // Permission
            "PERMISSION_READ", "READ_PERMISSION", "PERMISSION_ASSIGN", "ASSIGN_PERMISSION", "MANAGE_PERMISSIONS", "MANAGE_PERMISSION",
            // Config
            "CONFIG_CREATE", "CREATE_CONFIGURATION", "CREATE_CONFIG", "CONFIG_READ", "READ_CONFIGURATION", "CONFIG_UPDATE", "UPDATE_CONFIGURATION", "CONFIG_DELETE", "DELETE_CONFIGURATION", "MANAGE_CONFIGURATIONS", "MANAGE_CONFIGURATION",
            // Integration
            "INTEGRATION_CREATE", "CREATE_INTEGRATION", "INTEGRATION_READ", "READ_INTEGRATION", "INTEGRATION_UPDATE", "UPDATE_INTEGRATION", "INTEGRATION_DELETE", "DELETE_INTEGRATION", "MANAGE_INTEGRATIONS", "MANAGE_INTEGRATION",
            // File
            "FILE_CREATE", "CREATE_FILE", "FILE_READ", "READ_FILE", "FILE_UPDATE", "UPDATE_FILE", "FILE_DELETE", "DELETE_FILE", "MANAGE_FILES", "MANAGE_FILE",
            // Org
            "CREATE_ORGANISATION"
    };

    // Business / Investigation capabilities – 12 new global permissions (71 total)
    private static final String[][] BUSINESS_PERMISSIONS = {
            {"INVESTIGATION_ACCESS", "Access and manage investigations."},
            {"EVIDENCE_ACCESS", "Access and manage investigation evidence."},
            {"EVIDENCE_SOURCE_ACCESS", "Access evidence from connected enterprise sources."},
            {"EVIDENCE_GRAPH_ACCESS", "Access and explore the evidence graph."},
            {"TRACEABILITY_ACCESS", "Access forward and backward traceability capabilities."},
            {"TIMELINE_ACCESS", "Access investigation timelines and timeline analysis."},
            {"AI_INVESTIGATION_ACCESS", "Access AI-assisted investigation capabilities."},
            {"WORKSPACE_ACCESS", "Access the investigator workspace."},
            {"RECOMMENDATION_ACCESS", "Access and manage investigation recommendations."},
            {"DECISION_ACCESS", "Access and manage investigation decisions."},
            {"REPORT_ACCESS", "View, generate, and export investigation reports."},
            {"INVESTIGATION_ADMIN", "Manage higher-level investigation controls."}
    };

    @Bean
    public ApplicationRunner seedPermissions(PermissionRepository permissionRepository) {
        return args -> {
            for (String name : PERMISSIONS) {
                try {
                    if (!permissionRepository.existsByName(name)) {
                        permissionRepository.save(Permission.builder().name(name).description("Auto-seeded permission " + name).build());
                        log.info("Seeded permission {}", name);
                    }
                } catch (Exception e) {
                    log.warn("Skipping permission seeding, DB unavailable: {}", e.getMessage());
                    break;
                }
            }
            for (String[] entry : BUSINESS_PERMISSIONS) {
                String name = entry[0];
                String desc = entry[1];
                try {
                    if (!permissionRepository.existsByName(name)) {
                        permissionRepository.save(Permission.builder().name(name).description(desc).build());
                        log.info("Seeded permission {}", name);
                    }
                } catch (Exception e) {
                    log.warn("Skipping permission seeding, DB unavailable: {}", e.getMessage());
                    break;
                }
            }
        };
    }
}
