package com.taceiq.dto;

import com.taceiq.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String status;
    private Boolean mustChangePassword;
    private OrganisationSummary organisation;
    private RoleSummary role;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrganisationSummary {
        private Long orgId;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoleSummary {
        private Long id;
        private String name;
        private String description;
    }

    public static UserResponse fromEntity(User user) {
        if (user == null) return null;
        OrganisationSummary orgDto = null;
        if (user.getOrganisation() != null) {
            try {
                Long orgId = user.getOrganisation().getOrgId();
                String orgName = user.getOrganisation().getName();
                orgDto = OrganisationSummary.builder()
                        .orgId(orgId)
                        .name(orgName)
                        .build();
            } catch (Exception ignored) {
                // If proxy cannot be initialized (session closed), fallback to null safely
                orgDto = null;
            }
        }
        RoleSummary roleDto = null;
        if (user.getRole() != null) {
            try {
                roleDto = RoleSummary.builder()
                        .id(user.getRole().getId())
                        .name(user.getRole().getName())
                        .description(user.getRole().getDescription())
                        .build();
            } catch (Exception ignored) {
                roleDto = null;
            }
        }
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .status(user.getStatus())
                .mustChangePassword(user.getMustChangePassword())
                .organisation(orgDto)
                .role(roleDto)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
